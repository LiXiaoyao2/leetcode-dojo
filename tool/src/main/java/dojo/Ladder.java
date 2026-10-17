package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示阶梯：档位定义、提示词、历史读写、越界检查。
 *
 * <p>整个工具的核心就是这一页。设计上只保留「一次请求」：
 * 档位、题面、代码、测试输出、已给提示全在一份 prompt 里，模型直接产出下一档。
 * 不再有「诊断器 + 引导器 + 纪律检查器」三段式——那种结构会把提示变成模板，
 * 而这题的真正价值在于模型读过你的代码后说出的那一句具体的话。
 */
public final class Ladder {

    public record Level(int number, String name, int budget) {

        public static final Level[] ALL = {
                new Level(1, "澄清", 2500),
                new Level(2, "方向", 2500),
                new Level(3, "洞察", 2500),
                new Level(4, "骨架", 3000),
                new Level(5, "题解", 4500),
        };

        public static Level of(int number) {
            for (Level level : ALL) {
                if (level.number == number) {
                    return level;
                }
            }
            throw new DojoException("档位只能是 1–5");
        }

        static Level next(List<Turn> history) {
            int max = history.stream().mapToInt(Turn::level).max().orElse(0);
            return of(Math.min(5, max + 1));
        }
    }

    public record Turn(int level, String header, String body) {
    }

    public record HintResult(Level level, String text, boolean leak, boolean truncated, long millis,
                             int inputTokens, int outputTokens, String model) {
    }

    public static final String SYSTEM = """
            你是「算法教练」，陪一位准备 Java 面试的工程师刷 LeetCode。他正在 IDEA 里自己写题，卡住时才叫你。你的任务只有一个：按提示阶梯把他往前推「恰好一格」。

            阶梯：
            L1 澄清：只针对题意和他的代码。点出最容易被读漏的约束或边界，或他代码与题面的一处偏差，然后提出 1–2 个具体、能一句话回答的问题。不透露任何算法方向。
            L2 方向：指出这题属于哪一类模式（如「滑动窗口」「单调栈」），解释为什么这个模式的形状适配这组输入；点出过程中需要维护的量或不变量。不给步骤。
            L3 洞察：给出打破僵局的那一个观察，写成「因为……所以只要……」的形式，讲清它消掉了哪个瓶颈；可以点名他代码里的变量、条件或行号。不给步骤。
            L4 骨架：给 3–6 步有序步骤、目标复杂度、一个最易错点。允许伪代码和变量名，不允许出现能直接编译的 Java。
            L5 题解：给完整可提交的 Java 解法（```java 代码块），然后三句话讲思路、两句话讲正确性、一行时间/空间复杂度，最后给一个追问变体。

            硬规则：
            1. 中文，直接给内容。不要「好的」「让我看看」「以下是」这类开场白；不要 Markdown 标题（##）；不要表情符号。
            2. 不复述题面，不灌鸡汤，不说「再想想」「你可以的」「加油」。
            3. 【已经给过的提示】里出现过的内容一句都不许重复，换个说法也不行。每一档必须是新信息，并且承接上一档往下走。
            4. 篇幅：L1 ≤120 字，L2 ≤150 字，L3 ≤220 字，L4 ≤350 字，L5 不限。
            5. 只有 L1 可以用问句结尾，而且必须具体；L2–L4 用陈述句收尾。
            6. 他的代码若有明确 bug：L3 及以后指出具体位置与原因；L1/L2 只说「哪一类问题」。引用行号必须与【我当前的代码】里标注的行号逐字对应，拿不准就别写行号，绝不许估一个数。
            7. 如果【最近一次测试输出】显示全绿，或他的代码已经正确：先说「测试已经全绿」，然后把这一档用在更优解、边界、常数因子上，不要再让他去跑 dojo test。
            8. 不确定的地方说不确定，不要编造题目约束。
            """;

    private static final Pattern TURN_HEADER = Pattern.compile("^## L(\\d+) · ([^\\n]*)$");
    private static final Pattern JAVA_CLASS = Pattern.compile("\\b(class|interface|enum)\\s+[A-Z]\\w*\\s*(<[^>]*>)?\\s*(extends|implements)?");
    private static final Pattern JAVA_METHOD = Pattern.compile(
            "(?m)^\\s*(public|private|protected)\\s+[\\w<>\\[\\], ?]+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{");

    private final Ai ai;

    public Ladder(Ai ai) {
        this.ai = ai;
    }

    /** 读 hints.md。文件不存在就是「一档都没给过」。 */
    public static List<Turn> readHistory(Path hintsFile) {
        if (!Files.isRegularFile(hintsFile)) {
            return List.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(hintsFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DojoException("读不了 " + hintsFile + "：" + e.getMessage(), e);
        }
        List<Turn> turns = new ArrayList<>();
        int level = 0;
        String header = null;
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            Matcher matcher = TURN_HEADER.matcher(line);
            if (matcher.matches()) {
                if (level > 0) {
                    turns.add(new Turn(level, header, body.toString().strip()));
                }
                level = Integer.parseInt(matcher.group(1));
                header = matcher.group(2).strip();
                body.setLength(0);
            } else if (level > 0) {
                body.append(line).append('\n');
            }
        }
        if (level > 0) {
            turns.add(new Turn(level, header, body.toString().strip()));
        }
        return turns;
    }

    /** 追加一档。只有模型真的回了内容才调用，失败不写。 */
    public static void append(Path hintsFile, Level level, String note, String text) {
        StringBuilder sb = new StringBuilder();
        if (!Files.isRegularFile(hintsFile)) {
            String id = hintsFile.getParent().getFileName().toString();
            sb.append("# 提示记录 · ").append(id).append("\n\n");
            sb.append("> 每次求提示自动追加；下一次请求会把这里的内容全部带上，避免重复。\n\n");
        }
        sb.append("## L").append(level.number()).append(" · ").append(level.name()).append(" · ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append('\n');
        if (note != null && !note.isBlank()) {
            sb.append("> 卡点：").append(note.replaceAll("\\s+", " ").strip()).append('\n');
        }
        sb.append('\n').append(text.strip()).append("\n\n");
        try {
            Files.createDirectories(hintsFile.getParent());
            Files.writeString(hintsFile, sb.toString(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new DojoException("写 " + hintsFile + " 失败：" + e.getMessage(), e);
        }
    }

    public HintResult hint(String statement, String code, String testLog, List<Turn> history,
                           Level level, String note) {
        String user = userPrompt(statement, code, testLog, history, level, note);
        Ai.Reply reply = ai.complete(SYSTEM, user, level.budget());

        if (reply.truncated()) {
            Ai.Reply bigger = retry(user + "\n\n（上一次回复被长度上限截断了，这次务必在规定篇幅内收尾。）",
                    Math.min(8000, level.budget() * 2));
            if (bigger != null && !bigger.text().isBlank()) {
                reply = bigger;
            }
        }

        boolean leak = false;
        if (level.number() <= 4 && looksLikeJava(reply.text())) {
            String retryUser = user + "\n\n（提醒：上一次回复出现了完整可运行的 Java 代码，L" + level.number()
                    + " 不允许。重写这一档，只给「" + level.name() + "」该有的内容。）";
            Ai.Reply second = retry(retryUser, level.budget());
            if (second != null && !second.text().isBlank() && !looksLikeJava(second.text())) {
                reply = second;
            } else {
                leak = true;
            }
        }

        return new HintResult(level, reply.text(), leak, reply.truncated(), reply.millis(),
                reply.inputTokens(), reply.outputTokens(), ai.model());
    }

    private Ai.Reply retry(String user, int budget) {
        try {
            return ai.complete(SYSTEM, user, budget);
        } catch (DojoException e) {
            return null;
        }
    }

    public static String userPrompt(String statement, String code, String testLog, List<Turn> history,
                                    Level level, String note) {
        StringBuilder sb = new StringBuilder();
        sb.append("【题目】\n").append(clip(statement, 9000)).append("\n\n");
        sb.append("【我当前的代码（行号是文件里的真实行号）】\n");
        sb.append(code == null || code.isBlank() ? "（还没动笔）" : clipMiddle(code, 9000)).append("\n\n");
        sb.append("【最近一次测试输出】\n");
        sb.append(testLog == null || testLog.isBlank() ? "（还没跑过）" : clipTail(testLog, 3000)).append("\n\n");
        sb.append("【已经给过的提示】\n");
        if (history.isEmpty()) {
            sb.append("（无，这是第一档）\n\n");
        } else {
            for (Turn turn : history) {
                sb.append("### L").append(turn.level()).append(" · ").append(turn.header()).append('\n')
                        .append(clip(turn.body(), 900)).append("\n\n");
            }
        }
        sb.append("【这一档】L").append(level.number()).append(" · ").append(level.name()).append('\n');
        sb.append(instruction(level)).append("\n\n");
        sb.append("【我自己的卡点（没写就是「说不清哪里不对」）】\n")
                .append(note == null || note.isBlank() ? "（没说）" : note.strip()).append("\n\n");
        sb.append("只输出这一档提示正文。");
        return sb.toString();
    }

    private static String instruction(Level level) {
        return switch (level.number()) {
            case 1 -> "用 1–2 个具体问题把题意/边界对齐，不透露方向。";
            case 2 -> "点名模式，并解释它为什么适配这里的输入形状；不给步骤。";
            case 3 -> "给那一个关键观察，并说明它消掉了哪个瓶颈。";
            case 4 -> "给有序步骤、目标复杂度、一个最易错点；不要出现可编译的 Java。";
            default -> "给完整 Java 解法与讲解，最后给一个追问变体。";
        };
    }

    /** L1–L4 不该出现完整方法/类定义；出现就重写一次。 */
    public static boolean looksLikeJava(String text) {
        if (text == null) {
            return false;
        }
        if (JAVA_CLASS.matcher(text).find()) {
            return true;
        }
        return JAVA_METHOD.matcher(text).find();
    }

    static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n…（已截断）";
    }

    static String clipTail(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : "…（前文已截断）\n" + text.substring(text.length() - max);
    }

    /** 头和尾都留，代码的 import/类声明和「写到一半的地方」通常都在两端。 */
    static String clipMiddle(String text, int max) {
        if (text == null || text.length() <= max) {
            return text == null ? "" : text;
        }
        int head = max * 2 / 3;
        int tail = max - head;
        return text.substring(0, head) + "\n…（中间省略）…\n" + text.substring(text.length() - tail);
    }
}
