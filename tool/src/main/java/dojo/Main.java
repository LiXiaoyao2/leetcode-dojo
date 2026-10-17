package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** 命令入口。除了分发和打印，这里不放逻辑——每件事都有单独的类。 */
public final class Main {

    public static void main(String[] args) {
        int code;
        try {
            code = run(args);
        } catch (DojoException e) {
            Ui.err(e.getMessage());
            code = 1;
        } catch (RuntimeException e) {
            Ui.err("意外错误：" + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : "：" + e.getMessage()));
            if (System.getenv("DOJO_DEBUG") != null || hasDebug(args)) {
                e.printStackTrace();
            } else {
                Ui.info("设 DOJO_DEBUG=1 或加 --debug 看堆栈。");
            }
            code = 1;
        }
        System.exit(code);
    }

    private static boolean hasDebug(String[] args) {
        return Arrays.asList(args).contains("--debug");
    }

    private static int run(String[] args) {
        String command = args.length == 0 ? "help" : args[0];
        Args rest = Args.parse(Arrays.asList(args).subList(Math.min(1, args.length), args.length));
        return switch (command) {
            case "help", "-h", "--help" -> help();
            case "new", "n" -> createProblem(rest);
            case "hint", "h" -> hint(rest);
            case "test", "t" -> test(rest);
            case "status", "s" -> status();
            case "done", "d" -> done(rest);
            case "prompt", "p" -> prompt(rest);
            case "doctor" -> doctor();
            default -> {
                Ui.err("不认识的命令：" + command);
                Ui.info("dojo help 看用法。");
                yield 2;
            }
        };
    }

    // ------------------------------------------------------------------ 参数

    private static final class Args {
        final List<String> rest = new ArrayList<>();
        String problem;
        String note;
        boolean force;
        boolean noTest;
        boolean noVerify;
        boolean unordered;
        boolean noSave;
        boolean debug;

        static Args parse(List<String> tokens) {
            Args args = new Args();
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                switch (token) {
                    case "--force" -> args.force = true;
                    case "--no-test" -> args.noTest = true;
                    case "--no-verify" -> args.noVerify = true;
                    case "--unordered" -> args.unordered = true;
                    case "--no-save" -> args.noSave = true;
                    case "--debug" -> args.debug = true;
                    case "--problem", "-p" -> {
                        if (i + 1 >= tokens.size()) {
                            throw new DojoException("--problem 后面要跟题号或 slug。");
                        }
                        args.problem = tokens.get(++i);
                    }
                    case "--note", "-n" -> {
                        if (i + 1 >= tokens.size()) {
                            throw new DojoException("--note 后面要跟一句你的卡点。");
                        }
                        args.note = tokens.get(++i);
                    }
                    default -> {
                        if (token.startsWith("--problem=")) {
                            args.problem = token.substring("--problem=".length());
                        } else if (token.startsWith("--note=")) {
                            args.note = token.substring("--note=".length());
                        } else if (token.startsWith("-")) {
                            throw new DojoException("不认识的选项：" + token + "（dojo help）");
                        } else {
                            args.rest.add(token);
                        }
                    }
                }
            }
            return args;
        }

        String one(String what) {
            if (rest.size() > 1) {
                throw new DojoException(what + "只接受一个位置参数，多出来的：" + rest.subList(1, rest.size())
                        + "（用引号包住？）");
            }
            return rest.isEmpty() ? null : rest.get(0);
        }
    }

    // ------------------------------------------------------------------ 命令

    private static int help() {
        System.out.println("""
                dojo — 在 IDE 里刷题，卡住时按阶梯要提示

                用法
                  dojo new <slug|url>       拉一道题：题面 + Java 模板 + 官方样例测试
                  dojo hint [1-5|卡点]      要下一档提示（也可以指定档位，或直接写你的卡点）
                  dojo test                 跑当前题的单测，日志会留给下一次 hint
                  dojo status               看所有题的进度
                  dojo done                 把这题标记为完成
                  dojo prompt [1-5]         只打印提示词，不发请求（调试用）
                  dojo doctor               检查配置、网关和 Java/Maven

                选项
                  -p, --problem <id>        指定题目（默认按当前目录推断）
                  -n, --note <text>         告诉模型你卡在哪，提示会更准
                      --force               覆盖已存在的题面/解法/测试
                      --no-test             拉题时不生成测试
                      --no-verify           生成测试后不做编译校验
                      --unordered           答案与顺序无关时，按排序后比较
                      --no-save             这次提示不写进 hints.md
                      --debug               打印完整请求（hint）与异常堆栈

                五档提示
                  L1 澄清    只问问题，不给方向          L2 方向    点名套路和适用原因
                  L3 洞察    打破僵局的那一个观察        L4 骨架    步骤 + 复杂度，无完整代码
                  L5 题解    完整代码 + 证明 + 复杂度

                日常循环
                  dojo new two-sum      # 拉题
                  dojo test             # 先跑一遍，红是正常的
                  dojo hint             # 卡住就要下一档，不卡就别要
                  dojo done             # 过了就收工
                """);
        return 0;
    }

    private static int createProblem(Args args) {
        String input = args.one("new");
        if (input == null) {
            throw new DojoException("用法：dojo new <slug|url>，例如 dojo new two-sum");
        }
        String slug = Naming.looksNumeric(input) ? LeetCode.slugByNumber(input) : Naming.slugOf(input);
        if (slug == null || slug.isBlank()) {
            throw new DojoException("从「" + input + "」里没解析出题目标识。");
        }
        Repo repo = Repo.open();
        Ui.step("从 leetcode.cn 拉取 " + slug + " …");
        Problem problem = LeetCode.fetch(slug);
        Ui.info(problem.id() + "  " + problem.title() + "  ·  " + problem.difficultyZh()
                + (problem.tags().isEmpty() ? "" : "  ·  " + String.join(" / ", problem.tags())));

        Scaffold.Outcome outcome = Scaffold.create(repo, problem, args.force, args.noTest,
                args.noVerify, args.unordered);

        System.out.println();
        Ui.ok("已就绪：" + outcome.id());
        printPath(repo, repo.problemMd(outcome.id()), outcome.problemWritten() ? "新建" : "已存在");
        printPath(repo, repo.solutionFile(outcome.id()), outcome.solutionWritten() ? "新建" : "已存在");
        if (outcome.testFile() != null) {
            printPath(repo, outcome.testFile(), outcome.testWritten() ? "新建" : "已存在");
        }
        if (outcome.note() != null) {
            Ui.info(outcome.note());
        }
        if (outcome.warning() != null) {
            Ui.warn(outcome.warning());
        }
        System.out.println();
        System.out.println("下一步");
        System.out.println("  1. 打开 " + repo.root().relativize(repo.solutionFile(outcome.id())));
        System.out.println("  2. 卡住时：dojo hint");
        System.out.println("  3. 写完：dojo test");
        return 0;
    }

    private static void printPath(Repo repo, Path path, String state) {
        System.out.println("  " + state + "  " + repo.root().relativize(path));
    }

    private static int hint(Args args) {
        Repo repo = Repo.open();
        String id = repo.resolve(args.problem);
        Path markdown = repo.problemMd(id);
        if (!Files.isRegularFile(markdown)) {
            throw new DojoException("本地还没有 " + id + " 的题面。先 `dojo new " + Naming.slug(id) + "`。");
        }
        String statement = read(markdown);
        Path solution = repo.solutionFile(id);
        String code = Files.isRegularFile(solution) ? read(solution) : null;
        if (code == null) {
            Ui.warn("还没找到 " + repo.root().relativize(solution) + "，这次提示只基于题面。");
        }
        String testLog = Mvn.lastTestFor(repo, id);
        Path hintsFile = repo.hintsMd(id);
        List<Ladder.Turn> history = Ladder.readHistory(hintsFile);

        String first = args.one("hint");
        int requested = parseLevel(first);
        String note = args.note;
        Ladder.Level level;
        if (requested > 0) {
            level = Ladder.Level.of(requested);
        } else {
            level = Ladder.Level.next(history);
            if (first != null) {
                note = note == null ? first : first + "\n" + note;
            }
        }
        boolean repeated = history.stream().anyMatch(turn -> turn.level() == level.number());
        if (repeated) {
            Ui.warn("L" + level.number() + " 之前已经给过；这次算重问，内容可能不一样，也可能还是同一个方向。");
        }

        Config.Gateway gateway = Config.load(repo);
        Ai ai = new Ai(gateway);
        if (args.debug) {
            Ui.info("端点 " + gateway.endpoint() + " · 模型 " + gateway.model() + " · 来源 " + gateway.source());
            System.out.println("── 请求 ────────────────────────────────");
            System.out.println(Ladder.SYSTEM);
            System.out.println("── user ────────────────────────────────");
            System.out.println(Ladder.userPrompt(statement, code, testLog, history, level, note));
            System.out.println("────────────────────────────────────────");
        }
        Ui.step("请求 L" + level.number() + " · " + level.name() + "（" + gateway.model() + "）…");

        Ladder ladder = new Ladder(ai);
        Ladder.HintResult result = ladder.hint(statement, code, testLog, history, level, note);

        System.out.println();
        Ui.title("L" + level.number() + " · " + level.name());
        System.out.println();
        System.out.println(result.text().strip());
        System.out.println();
        Ui.info(oneLine(result.millis()) + " · " + result.model());
        if (result.truncated()) {
            Ui.warn("这次回复被长度上限截断了（已重试一次）。可以再 `dojo hint` 要下一档，或加 --note 让我写短点。");
        }
        if (result.leak()) {
            Ui.warn("这一档本不该出现完整 Java 代码，模型没守住，重写一次也没改掉。原文照发，你自己取舍。");
        }

        if (args.noSave) {
            Ui.info("（--no-save：这一档没有写进 hints.md，下次还会从同一档继续）");
        } else {
            try {
                Ladder.append(hintsFile, level, note, result.text());
                List<Ladder.Turn> after = Ladder.readHistory(hintsFile);
                if (level.number() < 5) {
                    Ui.info("下一档：dojo hint → L" + (level.number() + 1) + " · "
                            + Ladder.Level.of(level.number() + 1).name()
                            + "（现在共 " + after.size() + " 档）");
                } else {
                    Ui.info("L5 是最后一档。去写，然后 dojo test。");
                }
            } catch (DojoException e) {
                Ui.warn("提示已经给出，但没写进 hints.md：" + e.getMessage());
            }
        }
        return 0;
    }

    /** 0 = 不是档位（当成卡点文本），1–5 = 档位；6 以上直接报错。 */
    private static int parseLevel(String value) {
        if (value == null) {
            return 0;
        }
        String text = value.strip();
        if (text.length() == 2 && (text.charAt(0) == 'L' || text.charAt(0) == 'l')) {
            text = text.substring(1);
        }
        if (text.matches("\\d{1,2}")) {
            int number = Integer.parseInt(text);
            if (number >= 1 && number <= 5) {
                return number;
            }
            throw new DojoException("档位只有 1–5（L1 澄清 … L5 题解）。");
        }
        return 0;
    }

    private static int test(Args args) {
        Repo repo = Repo.open();
        String id = repo.resolve(args.problem);
        String testClass = repo.testClass(id);
        Path testFile = repo.testFile(id);
        if (!Files.isRegularFile(testFile)) {
            throw new DojoException("本地没有 " + repo.root().relativize(testFile)
                    + "，先 `dojo new " + Naming.slug(id) + "`（已有的题可以 `dojo new " + Naming.slug(id)
                    + " --force` 重新生成测试）。");
        }
        Ui.step("mvn test -Dtest=" + testClass + " …");
        Mvn.Result result = Mvn.test(repo.root(), testClass);
        Mvn.saveLastTest(repo, id, testClass, result);
        if (!result.available()) {
            Ui.err(result.output());
            return 1;
        }
        if (isCompileFailure(result)) {
            Ui.err("编译失败 · " + oneLine(result.millis()));
            System.out.println();
            System.out.println(Mvn.summarize(result));
            System.out.println();
            Ui.info("先把编译错误清掉——断言还没开始跑。");
            return 1;
        }
        if (result.ok()) {
            Ui.ok("通过 · " + oneLine(result.millis()));
        } else {
            Ui.err("没通过 · " + oneLine(result.millis()) + "（exit " + result.exit() + "）");
        }
        System.out.println();
        System.out.println(Mvn.summarize(result));
        System.out.println();
        if (result.ok()) {
            Ui.info("过了就 `dojo done`；想继续打磨就 `dojo hint` 要下一档。");
        } else {
            Ui.info("别急着改代码：`dojo hint` 会读这次失败输出。");
        }
        return result.ok() ? 0 : 1;
    }

    private static int status() {
        Repo repo = Repo.open();
        List<String> ids = repo.problemIds();
        if (ids.isEmpty()) {
            Ui.info("还没有题。`dojo new two-sum` 开始。");
            return 0;
        }
        int done = 0;
        for (String id : ids) {
            List<Ladder.Turn> history = Ladder.readHistory(repo.hintsMd(id));
            boolean finished = repo.isDone(id);
            if (finished) {
                done++;
            }
            String progress;
            if (history.isEmpty()) {
                progress = "还没要过提示";
            } else {
                int max = history.stream().mapToInt(Ladder.Turn::level).max().orElse(0);
                progress = "提示 L1–L" + max + "（" + history.size() + " 条）";
            }
            System.out.println(id + "  " + repo.title(id) + "  ·  " + progress + "  ·  "
                    + (finished ? "已完成 ✓" : "未完成"));
        }
        System.out.println();
        Ui.info("共 " + ids.size() + " 道，已完成 " + done + " 道。");
        return 0;
    }

    private static int done(Args args) {
        Repo repo = Repo.open();
        String id = repo.resolve(args.problem);
        if (repo.isDone(id)) {
            Ui.info(id + " 早就标记过完成了。");
            return 0;
        }
        try {
            Path marker = repo.doneMarker(id);
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, LocalDateTime.now() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DojoException("写完成标记失败：" + e.getMessage());
        }
        Ui.ok(id + " 完成 ✓");
        Ui.info("完成标记在 problems/" + id + "/done。`dojo status` 能看到。");
        return 0;
    }

    private static int prompt(Args args) {
        Repo repo = Repo.open();
        String id = repo.resolve(args.problem);
        String statement = read(repo.problemMd(id));
        Path solution = repo.solutionFile(id);
        String code = Files.isRegularFile(solution) ? read(solution) : null;
        String testLog = Mvn.lastTestFor(repo, id);
        List<Ladder.Turn> history = Ladder.readHistory(repo.hintsMd(id));
        String first = args.one("prompt");
        int requested = parseLevel(first);
        Ladder.Level level = requested > 0 ? Ladder.Level.of(requested) : Ladder.Level.next(history);
        String note = requested > 0 ? args.note : (first == null ? args.note
                : (args.note == null ? first : first + "\n" + args.note));

        Config.Gateway gateway = Config.load(repo);
        System.out.println("模型 " + gateway.model() + " · 端点 " + gateway.endpoint() + " · "
                + gateway.source() + " · L" + level.number() + " " + level.name());
        Ui.line();
        System.out.println(Ladder.SYSTEM);
        Ui.line();
        System.out.println(Ladder.userPrompt(statement, code, testLog, history, level, note));
        return 0;
    }

    private static int doctor() {
        Repo repo = Repo.open();
        System.out.println("java    " + System.getProperty("java.version") + "  (" + System.getProperty("java.vendor") + ")");
        System.out.println("mvn     " + mvnVersion());
        System.out.println("仓库    " + repo.root());
        System.out.println();
        System.out.println("配置来源（按优先级，取第一个有 base URL 的）");
        for (String line : Config.sourcesForDoctor(repo)) {
            System.out.println("  · " + line);
        }
        System.out.println();
        Config.Gateway gateway;
        try {
            gateway = Config.load(repo);
        } catch (DojoException e) {
            Ui.err(e.getMessage());
            return 1;
        }
        System.out.println("使用    " + gateway.source());
        System.out.println("base    " + gateway.baseUrl());
        System.out.println("端点    " + gateway.endpoint());
        System.out.println("模型    " + gateway.model());
        System.out.println("协议    " + gateway.provider());
        System.out.println("token   " + gateway.maskedToken());
        System.out.println();
        Ui.step("ping …");
        try {
            Ai.Reply reply = new Ai(gateway).complete("你是连通性检查，只回复 OK。", "只回复 OK。", 64);
            String text = reply.text() == null ? "" : reply.text().strip().replaceAll("\\s+", " ");
            if (text.length() > 40) {
                text = text.substring(0, 40) + "…";
            }
            Ui.ok("ping 成功 · " + oneLine(reply.millis()) + " · " + text
                    + (reply.truncated() ? "（被截断，但通道是通的）" : ""));
            return 0;
        } catch (DojoException e) {
            Ui.err("ping 失败：" + e.getMessage());
            return 1;
        }
    }

    /** Maven 编译失败时 surefire 根本不会启动，不能把它当成「测试失败」来读。 */
    private static boolean isCompileFailure(Mvn.Result result) {
        String output = result.output();
        return output.contains("COMPILATION ERROR") || output.contains("Compilation failure")
                || (output.contains("BUILD FAILURE") && !output.contains("Tests run:"));
    }

    private static String mvnVersion() {
        try {
            Process process = new ProcessBuilder("mvn", "-v").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            for (String line : output.split("\n")) {
                if (line.startsWith("Apache Maven")) {
                    return line.strip();
                }
            }
            return output.lines().findFirst().map(String::strip).orElse("?");
        } catch (IOException e) {
            return "找不到 mvn（拉题测试、dojo test 都要它）";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "读取 mvn 版本被中断";
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DojoException("读 " + path + " 失败：" + e.getMessage());
        }
    }

    private static String oneLine(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        return String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
    }
}
