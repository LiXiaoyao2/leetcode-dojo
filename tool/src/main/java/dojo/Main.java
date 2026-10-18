package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
        if (command.equals("eval")) {
            String[] rest = Arrays.copyOfRange(args, 1, args.length);
            if (Arrays.asList(rest).contains("--report")) {
                return Eval.reportFromRaw(Repo.open());
            }
            for (int i = 0; i < rest.length - 1; i++) {
                if (rest[i].equals("--noise")) {
                    return Eval.noise(Repo.open(), Integer.parseInt(rest[i + 1]));
                }
            }
            return Eval.run(rest);
        }
        if (command.equals("learn")) {
            return Learn.run(Repo.open(), Arrays.copyOfRange(args, 1, args.length));
        }
        if (command.equals("fade")) {
            return Fade.run(Repo.open(), Arrays.copyOfRange(args, 1, args.length));
        }
        Args rest = Args.parse(Arrays.asList(args).subList(Math.min(1, args.length), args.length));
        return switch (command) {
            case "help", "-h", "--help" -> help();
            case "new", "n" -> createProblem(rest);
            case "hint", "h" -> hint(rest);
            case "test", "t" -> test(rest);
            case "status", "s" -> status(rest);
            case "done", "d" -> done(rest);
            case "prompt", "p" -> prompt(rest);
            case "doctor" -> doctor();
            case "clean", "c" -> clean(rest);
            case "stress" -> stressQuietly(rest);
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
        boolean noRun;
        boolean open;
        boolean debug;
        boolean noLearn;
        boolean all;
        boolean refresh;
        int cases = 300;
        long seedBase;

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
                    case "--no-run" -> args.noRun = true;
                    case "--open" -> args.open = true;
                    case "--no-learn" -> args.noLearn = true;
                    case "--debug" -> args.debug = true;
                    case "--all" -> args.all = true;
                    case "--refresh" -> args.refresh = true;
                    case "--cases" -> {
                        if (i + 1 >= tokens.size()) {
                            throw new DojoException("--cases 后面要跟次数，比如 --cases 300。");
                        }
                        args.cases = Integer.parseInt(tokens.get(++i));
                    }
                    case "--seed" -> {
                        if (i + 1 >= tokens.size()) {
                            throw new DojoException("--seed 后面要跟一个整数。");
                        }
                        args.seedBase = Long.parseLong(tokens.get(++i));
                    }
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
                        if (token.startsWith("--cases=")) {
                            args.cases = Integer.parseInt(token.substring("--cases=".length()));
                        } else if (token.startsWith("--seed=")) {
                            args.seedBase = Long.parseLong(token.substring("--seed=".length()));
                        } else if (token.startsWith("--problem=")) {
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

    private static String diffOf(String before, String code) {
        if (before == null || code == null) {
            return null;
        }
        String diff = Diff.unified(before, code, 3);
        return diff.isBlank() ? "（代码没动）" : diff;
    }

    private static boolean testsRanSince(Repo repo, Path snapshot) {
        Path log = repo.root().resolve(".dojo/last-test.log");
        if (!Files.isRegularFile(log)) {
            return false;
        }
        try {
            if (!Files.isRegularFile(snapshot)) {
                return true;
            }
            return Files.getLastModifiedTime(log).toMillis() > Files.getLastModifiedTime(snapshot).toMillis();
        } catch (IOException e) {
            return true;
        }
    }

    private static void saveSnapshot(Path snapshot, String code) {
        if (code == null) {
            return;
        }
        try {
            Files.createDirectories(snapshot.getParent());
            Files.writeString(snapshot, code, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Ui.warn("代码快照没写成，下次提示看不到 diff：" + e.getMessage());
        }
    }

    private static int help() {
        System.out.println("""
                dojo — 在 IDE 里刷题，卡住时按阶梯要提示

                用法
                  dojo new <slug|url|题号>  拉一道题：题面 + Java 模板 + 官方样例测试
                  dojo new 1-100            批量拉一个区间的题（一次最多 300 道）
                  dojo hint [1-5|卡点]      要下一档提示（也可以指定档位，或直接写你的卡点）
                  dojo test                 跑当前题的单测，日志会留给下一次 hint
                  dojo status               看所有题的进度
                  dojo done                 把这题标记为完成
                  dojo prompt [1-5]         只打印提示词，不发请求（调试用）
                  dojo stress               随机对拍：拿暴力解当对照，找样例测不出的错
                  dojo doctor               检查配置、网关和 Java/Maven
                  dojo clean                清构建产物；带题号删这道题（进系统回收站）
                  dojo eval                 跑提示质量评测（开发用，见 eval/RESULTS.md）
                  dojo eval --cases 3       先跑 3 题试水；--no-judge 只跑客观指标
                  dojo eval --report        只重建报告，不调模型
                  dojo eval --noise 5       判官重测同一答案，看主观分方差

                学习资料
                  dojo learn                列出 24 个模式
                  dojo learn monotonic-stack  识别信号、模板、前置题、巩固题
                  dojo fade                 把 L5 挖空 3–5 行，补完再 dojo test
                  dojo fade --restore       还原挖空前的解法
                  dojo fade --show          对照挖空前的原文

                选项
                  -p, --problem <id>        指定题目（默认按当前目录推断）
                  -n, --note <text>         告诉模型你卡在哪，提示会更准
                      --force               覆盖已存在的题面/解法/测试；hint 时忽略「代码没动」
                      --no-test             拉题时不生成测试
                      --no-verify           生成测试后不做编译校验
                      --unordered           答案与顺序无关时，按排序后比较
                      --no-save             这次提示不写进 hints.md
                      --no-run              不在要提示前自动重跑测试
                      --open                把这一档提示写进 last-hint.md 并尝试在 IDEA 里打开
                      --debug               打印完整请求（hint）与异常堆栈
                      --no-learn            L4 之后不追加「下一步」

                stress（随机对拍）
                  dojo stress               300 组随机输入，和 Brute.java 比结果
                  dojo stress --cases 1000  多跑一点
                  dojo stress --seed 42     换一批种子
                  dojo stress --refresh     重新让模型写暴力解

                清理
                  dojo clean                删 target/ 与 .dojo/last-test.log
                  dojo clean <题>           把这题的题面/解法/测试挪进系统回收站
                  dojo clean --all          所有题都挪进系统回收站

                评测（开发用）
                  dojo eval --snapshot      按 eval/benchmark.txt 拉评测集
                  dojo eval --cases 3       先跑 3 题试水，结果写到 eval/RESULTS.md
                  dojo eval                跑全量：20 题 × 3 种架构 × 5 档

                五档提示
                  L1 澄清    只问问题，不给方向          L2 方向    点名套路和适用原因
                  L3 洞察    打破僵局的那一个观察        L4 骨架    步骤 + 复杂度，无完整代码
                  L5 题解    完整代码 + 证明 + 复杂度

                日常循环
                  dojo new two-sum      # 拉题
                  dojo new 1-100        # 批量拉题
                  dojo test             # 先跑一遍，红是正常的
                  dojo hint             # 卡住就要下一档，不卡就别要
                  dojo done             # 过了就收工
                """);
        return 0;
    }


    /** 一次最多拉多少题：够覆盖「1-100」这种诉求，又不至于敲错一个数就把磁盘塞满。 */
    private static final int BATCH_LIMIT = 300;

    private static int createRange(Repo repo, Args args, int from, int to) {
        int total = to - from + 1;
        if (total > BATCH_LIMIT) {
            throw new DojoException("一次最多 " + BATCH_LIMIT + " 题，你给的是 " + from + "-" + to
                    + "（" + total + " 题）。分段来，比如 dojo new " + from + "-" + (from + BATCH_LIMIT - 1) + "。");
        }
        Ui.step("批量拉题 " + from + "-" + to + "（" + total + " 题）");
        List<String> failures = new ArrayList<>();
        Set<Path> generatedTests = new LinkedHashSet<>();
        int created = 0;
        int skipped = 0;
        for (int n = from; n <= to; n++) {
            String label = String.format(Locale.ROOT, "[%3d/%3d]", n - from + 1, total);
            String number = String.format(Locale.ROOT, "%04d", n);
            if (repo.find(number).isPresent() && !args.force) {
                System.out.printf(Locale.ROOT, "  %s %-58s 跳过（已存在）%n", label, number);
                skipped++;
                continue;
            }
            try {
                String slug = LeetCode.slugByNumber(String.valueOf(n));
                Problem problem = LeetCode.fetch(slug);
                Scaffold.Outcome outcome = Scaffold.create(repo, problem, args.force, args.noTest,
                        true, args.unordered);
                if (outcome.testFile() != null && outcome.testWritten()) {
                    generatedTests.add(outcome.testFile());
                }
                System.out.printf(Locale.ROOT, "  %s %-58s ✓%n", label, outcome.id());
                created++;
            } catch (DojoException e) {
                System.out.printf(Locale.ROOT, "  %s %-58s ✗ %s%n", label, number, oneLine(e.getMessage()));
                failures.add(number + "（" + e.getMessage() + "）");
            }
            if (n < to) {
                pause(150);
            }
        }

        System.out.println();
        Ui.ok("批量完成 · 新建 " + created + " · 跳过 " + skipped + " · 失败 " + failures.size());
        if (!failures.isEmpty()) {
            Ui.warn("失败的题");
            failures.stream().limit(10).forEach(failure -> System.out.println("  " + failure));
            if (failures.size() > 10) {
                System.out.println("  … 另有 " + (failures.size() - 10) + " 道");
            }
            Ui.info("重试：dojo new <题号>");
        }
        if (!generatedTests.isEmpty() && !args.noTest && !args.noVerify) {
            verifyBatch(repo, generatedTests);
        }
        return failures.isEmpty() ? 0 : 1;
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String oneLine(String message) {
        return message == null ? "" : message.replaceAll("\\s+", " ").strip();
    }

    /** 批量建题时每道题都跑一次 mvn 太慢，全部写完统一编译一次；哪道题的测试编不过就删哪道。 */
    private static void verifyBatch(Repo repo, Set<Path> generatedTests) {
        Ui.step("统一编译校验 " + generatedTests.size() + " 个新测试 …");
        Mvn.Result result = Mvn.testCompile(repo.root());
        if (result.ok()) {
            Ui.ok("测试全部编译通过");
            return;
        }
        Set<Path> broken = new LinkedHashSet<>();
        for (String line : result.output().split("\\R")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\\[ERROR\\]\\s+(/\\S+?\\.java):\\[\\d+,\\d+\\]").matcher(line);
            if (!matcher.find()) {
                continue;
            }
            Path path = Path.of(matcher.group(1)).normalize();
            if (generatedTests.contains(path)) {
                broken.add(path);
            }
        }
        if (broken.isEmpty()) {
            Ui.warn("统一编译没通过，但不是新生成的测试引起的：");
            Mvn.summarize(result).lines().limit(8).forEach(line -> System.out.println("  " + line));
            return;
        }
        for (Path path : broken) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                Ui.warn("删不掉 " + path + "：" + e.getMessage());
            }
        }
        Ui.warn("删掉 " + broken.size() + " 个编译不过的测试（避免拖垮整个模块）：");
        broken.forEach(path -> System.out.println("  " + repo.root().relativize(path)));
        Mvn.Result again = Mvn.testCompile(repo.root());
        if (again.ok()) {
            Ui.ok("删掉之后模块恢复可编译，其余题不受影响");
        } else {
            Ui.warn("还有编译错误，可能出在某个 Solution.java 上：");
            Mvn.summarize(again).lines().limit(8).forEach(line -> System.out.println("  " + line));
        }
    }

    private static int createProblem(Args args) {
        String input = args.one("new");
        if (input == null) {
            throw new DojoException("用法：dojo new <slug|url>，例如 dojo new two-sum");
        }
        Repo repo = Repo.open();
        if (Naming.looksRange(input)) {
            int[] bounds = Naming.range(input);
            return createRange(repo, args, bounds[0], bounds[1]);
        }
        String slug = Naming.looksNumeric(input) ? LeetCode.slugByNumber(input) : Naming.slugOf(input);
        if (slug == null || slug.isBlank()) {
            throw new DojoException("从「" + input + "」里没解析出题目标识。");
        }
        Ui.step("从 leetcode.cn 拉取 " + slug + " …");
        Problem problem = LeetCode.fetch(slug);
        Ui.info(problem.id() + "  " + problem.title() + "  ·  " + problem.difficultyZh()
                + (problem.tags().isEmpty() ? "" : "  ·  " + String.join(" / ", problem.tags())));

        String plannedId = problem.id();
        boolean hadProblem = Files.isRegularFile(repo.problemMd(plannedId));
        boolean hadSolution = Files.isRegularFile(repo.solutionFile(plannedId));
        boolean hadTest = Files.isRegularFile(repo.testFile(plannedId));
        if (args.force && (hadProblem || hadSolution || hadTest)) {
            Ui.warn("--force：下面这些已存在的文件会被覆盖（建议先 git status / git diff）");
        }
        Scaffold.Outcome outcome = Scaffold.create(repo, problem, args.force, args.noTest,
                args.noVerify, args.unordered);

        System.out.println();
        Ui.ok("已就绪：" + outcome.id());
        printPath(repo, repo.problemMd(outcome.id()), writeState(hadProblem, args.force));
        printPath(repo, repo.solutionFile(outcome.id()), writeState(hadSolution, args.force));
        if (outcome.testFile() != null) {
            printPath(repo, outcome.testFile(), writeState(hadTest, args.force));
        } else if (args.noTest) {
            System.out.println("  跳过  测试（--no-test）");
        } else {
            System.out.println("  跳过  测试（生成失败，原因见上）");
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
        if (outcome.testFile() != null) {
            System.out.println("  3. 写完：dojo test");
        } else {
            System.out.println("  3. 补测试：dojo new " + Naming.slug(outcome.id()) + "（不会覆盖 Solution）");
        }
        return 0;
    }

    private static String writeState(boolean existed, boolean force) {
        if (!existed) {
            return "新建";
        }
        return force ? "覆盖" : "已存在";
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
        // 刚 new 出来、一行没写的模板不当成「抛异常」——那会让第一次 hint 变成定位，而不是 L1。
        boolean untouched = code != null && code.contains("TODO: 还没写");
        if (!args.noRun && !untouched && code != null && Mvn.stale(repo, id, solution)) {
            Path testFile = repo.testFile(id);
            if (Files.isRegularFile(testFile)) {
                Ui.step("代码比上次测试新，先跑一遍测试…");
                Mvn.Result fresh = Mvn.test(repo.root(), repo.testClass(id));
                Mvn.saveLastTest(repo, id, repo.testClass(id), fresh);
                if (!fresh.available()) {
                    Ui.warn("没找到 mvn，这次跳过自动测试（--no-run 可以关掉这个行为）。");
                } else if (fresh.ok()) {
                    Ui.info("测试全绿。");
                } else {
                    Ui.info("测试还是红的，提示会带上这次的失败输出。");
                }
            }
        }
        String testLog = untouched ? null : Mvn.lastTestFor(repo, id);
        if (!untouched && testLog != null && testLog.contains("COMPILATION ERROR")) {
            String blocker = foreignErrorOwner(repo, id, testLog);
            if (blocker != null) {
                Ui.err("上次测试是编译失败，错在 " + blocker + "，不是这道题。");
                Ui.info("先把 " + blocker + " 编译过（dojo test -p " + Naming.number(blocker)
                        + " 看完整报错）；确实不要它再 dojo clean " + Naming.number(blocker) + "。");
                return 1;
            }
        }
        HintMode mode = untouched ? HintMode.LADDER : HintMode.detect(testLog);
        Path snapshot = repo.snapshotFile(id);
        String before = Files.isRegularFile(snapshot) ? read(snapshot) : null;
        String diff = diffOf(before, code);
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
        if (requested > 0) {
            // 明说「要第 4 档」时按阶梯走；卡点分流只在没指定档位时接管。
            mode = HintMode.LADDER;
        }
        boolean repeated = history.stream().anyMatch(turn -> turn.level() == level.number());
        if (repeated) {
            Ui.warn("L" + level.number() + " 之前已经给过；这次算重问，内容可能不一样，也可能还是同一个方向。");
        }

        boolean codeUnchanged = "（代码没动）".equals(diff);
        boolean testsNewer = testsRanSince(repo, snapshot);
        if (!args.force && requested <= 0 && codeUnchanged && !testsNewer
                && !history.isEmpty()) {
            Ui.warn("代码没动，也没有新的测试结果——先按 L" + history.get(history.size() - 1).level() + " 改一次。");
            Ui.info("确实想直接要下一档，加 --force。");
            return 1;
        }

        Config.Gateway gateway = Config.load(repo);
        Ai ai = new Ai(gateway);
        if (args.debug) {
            Ui.info("端点 " + gateway.endpoint() + " · 模型 " + gateway.model() + " · 来源 " + gateway.source());
            System.out.println("── 请求 ────────────────────────────────");
            System.out.println(Ladder.SYSTEM);
            System.out.println("── user ────────────────────────────────");
            System.out.println("（模式：" + mode.label() + "）");
            System.out.println(Ladder.userPrompt(statement, code, testLog, history, level, note, diff, mode));
            System.out.println("────────────────────────────────────────");
        }
        Ui.step(mode.countsAsLevel()
                ? "请求 L" + level.number() + " · " + level.name() + "（" + gateway.model() + "）…"
                : "请求 " + mode.label() + "（" + gateway.model() + "）…");

        Ladder ladder = new Ladder(ai);
        Ladder.HintResult result = ladder.hint(statement, code, testLog, history, level, note, diff, mode);

        System.out.println();
        String heading = switch (mode) {
            case LADDER -> "L" + level.number() + " · " + level.name();
            case COUNTEREXAMPLE -> "L" + level.number() + " · 反例";
            default -> mode.label();
        };
        Ui.title(heading);
        System.out.println();
        System.out.println(result.text().strip());
        System.out.println();
        String tail = null;
        if (!args.noLearn && mode == HintMode.LADDER && level.number() == 4) {
            String patternId = result.patternId() != null ? result.patternId() : Patterns.fromProblem(repo, id);
            if (patternId != null) {
                boolean shown = Patterns.seen(repo, patternId);
                String hint = Patterns.tail(repo, patternId, shown, id);
                if (hint != null) {
                    tail = hint;
                    System.out.println(hint);
                    System.out.println();
                    if (!shown) {
                        Patterns.markSeen(repo, patternId);
                    }
                }
            }
        }
        Ui.info(oneLine(result.millis()) + " · " + result.model());
        if (result.truncated()) {
            Ui.warn("这次回复被长度上限截断了（已重试一次）。可以再 `dojo hint` 要下一档，或加 --note 让我写短点。");
        }
        if (result.leak()) {
            Ui.warn("这一档本不该出现完整 Java 代码，模型没守住，重写一次也没改掉。原文照发，你自己取舍。");
        }

        if (args.open) {
            try {
                String body = tail == null ? result.text() : result.text() + "\n\n" + tail;
                Path hintFile = mode.countsAsLevel()
                        ? HintFile.write(repo, id, heading, note, body,
                                level.number() < 5
                                        ? "下一档：`dojo hint` → L" + (level.number() + 1) + " · "
                                                + Ladder.Level.of(level.number() + 1).name() + "\n"
                                        : "最后一档。去写，然后 `dojo test`。\n")
                        : HintFile.write(repo, id, mode.label(), note, result.text(),
                                "（这类提示不计入档位；hints.md 未变）\n");
                boolean opened = HintFile.openInIde(hintFile);
                Ui.info("提示已写入 " + repo.root().relativize(hintFile)
                        + (opened ? "（已在 IDEA 里打开）" : "（在 IDEA 里双击打开即可）"));
            } catch (IOException e) {
                Ui.warn("写 last-hint.md 失败：" + e.getMessage());
            }
        }

        if (!mode.countsAsLevel()) {
            saveSnapshot(snapshot, code);
            Ui.info("（" + mode.label() + "不计入档位，hints.md 未变；改完直接 dojo test 或再 hint）");
        } else if (args.noSave) {
            Ui.info("（--no-save：这一档没有写进 hints.md，下次还会从同一档继续）");
        } else {
            try {
                Ladder.append(hintsFile, level, mode == HintMode.COUNTEREXAMPLE ? "反例" : level.name(), note,
                        result.text());
                List<Ladder.Turn> after = Ladder.readHistory(hintsFile);
                saveSnapshot(snapshot, code);
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
        if (args.problem == null) {
            Ui.info("没指定题，用最近改动的 " + id + "（-p 可以指定别的）。");
        }
        String testClass = repo.testClass(id);
        Path testFile = repo.testFile(id);
        if (!Files.isRegularFile(testFile)) {
            throw new DojoException("本地没有 " + repo.root().relativize(testFile)
                    + "。补生成测试：`dojo new " + Naming.slug(id)
                    + "`（不会动你的 Solution；`--force` 才会覆盖）。");
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
            List<Path> removed = quarantineGeneratedTests(repo, result.output());
            if (!removed.isEmpty()) {
                Ui.warn("以上测试是生成器写的、编译不过，已移入回收站（要重生成：dojo new "
                        + Naming.slug(id) + " --force）：");
                for (Path path : removed) {
                    System.out.println("  " + repo.root().relativize(path));
                }
                Mvn.Result retry = Mvn.test(repo.root(), testClass);
                Mvn.saveLastTest(repo, id, testClass, retry);
                if (!isCompileFailure(retry)) {
                    Ui.info("清掉之后重跑：");
                    return report(repo, id, retry);
                }
                result = retry;
                Ui.err("清掉生成的测试后，编译仍然不过：");
                System.out.println();
                System.out.println(Mvn.summarize(retry));
                System.out.println();
            }
            Ui.info("先把编译错误清掉——断言还没开始跑。");
            foreignCompileFailures(repo, id, result.output());
            return 1;
        }
        return report(repo, id, result);
    }

    /** 跑完之后的统一出口：通过/失败 + 摘要 + 下一步。 */
    private static int report(Repo repo, String id, Mvn.Result result) {
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

    /**
     * 生成器写的测试如果编译不过，留着只会让所有题都跑不了。识别办法：文件里带生成标记。
     * 只清带标记的，手写的测试一个不碰。
     */
    private static List<Path> quarantineGeneratedTests(Repo repo, String output) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:^|\\s)(/[^\\s:]*?\\.java)(?=:\\[)").matcher(output);
        List<Path> doomed = new ArrayList<>();
        String generatedDir = repo.solutionsDir().resolve("src/test/java").toString() + java.io.File.separator;
        while (matcher.find()) {
            Path file = Path.of(matcher.group(1));
            if (!file.startsWith(generatedDir) || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                if (Files.readString(file, StandardCharsets.UTF_8).contains("// dojo 依据题目 metaData")) {
                    doomed.add(file);
                }
            } catch (IOException ignored) {
                // 读不了就当不是生成器写的
            }
        }
        if (doomed.isEmpty()) {
            return doomed;
        }
        Clean.trashFiles(doomed);
        return doomed;
    }

    private static int status(Args args) {
        Repo repo = Repo.open();
        String only = args.problem;
        List<String> ids = only == null ? repo.problemIds()
                : List.of(repo.find(only).orElseThrow(
                        () -> new DojoException("本地没有 " + only + "，没有可显示的。")));
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
        String diff = diffOf(Files.isRegularFile(repo.snapshotFile(id)) ? read(repo.snapshotFile(id)) : null, code);
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
        System.out.println(Ladder.userPrompt(statement, code, testLog, history, level, note, diff));
        return 0;
    }

    private static int stressQuietly(Args args) {
        try {
            return stress(args);
        } catch (IOException e) {
            Ui.err("读写文件失败：" + e.getMessage());
            return 1;
        }
    }

    private static int stress(Args args) throws IOException {
        Repo repo = Repo.open();
        String target = args.problem != null ? args.problem : args.one("stress");
        String id = repo.resolve(target);
        Config.Gateway gateway = Config.load(repo);
        return Stress.run(repo, id, new Stress.Options(args.cases, args.seedBase, args.refresh), new Ai(gateway));
    }

    private static int clean(Args args) {
        Repo repo = Repo.open();
        String target = args.problem != null ? args.problem : args.one("clean");
        if (args.all && target != null) {
            throw new DojoException("--all 不接受题号：要么 `dojo clean --all`，要么 `dojo clean <题>`。");
        }
        if (args.all) {
            List<String> ids = repo.problemIds();
            if (ids.isEmpty()) {
                Ui.info("还没有题目，不用清。");
                return 0;
            }
            List<Path> moved = Clean.trash(repo, ids);
            Ui.ok("已移入系统回收站：" + ids.size() + " 道题（" + moved.size() + " 个路径）");
            reportMoved(repo, moved);
            return 0;
        }
        if (target != null) {
            String id = repo.find(target).orElseThrow(
                    () -> new DojoException("本地没有 " + target + "，没有可删的。"));
            List<Path> moved = Clean.trash(repo, List.of(id));
            Ui.ok("已移入系统回收站：" + id);
            reportMoved(repo, moved);
            return 0;
        }

        Clean.Freed freed = Clean.buildArtifacts(repo);
        if (freed.paths().isEmpty()) {
            Ui.info("没有可清的东西：target/ 和 .dojo/last-test.log 都不在。");
            return 0;
        }
        Ui.ok("清理完成 · 释放 " + Clean.human(freed.bytes()));
        for (Path path : freed.paths()) {
            System.out.println("  " + repo.root().relativize(path));
        }
        Ui.info("工具 jar 若被删，下次运行 bin/dojo 会自动重编；题目和提示记录没动。");
        return 0;
    }

    private static void reportMoved(Repo repo, List<Path> moved) {
        for (Path path : moved) {
            System.out.println("  " + repo.root().relativize(path));
        }
        Ui.info("删错了就在系统回收站里拖回来（文件管理器 → 回收站）。");
    }

    /** 在 PATH 里找可执行的 dojo；找不到返回 null。 */
    private static String dojoOnPath() {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            java.nio.file.Path candidate = java.nio.file.Path.of(dir, "dojo");
            if (java.nio.file.Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static boolean pointsHere(String onPath, Repo repo) {
        try {
            return java.nio.file.Path.of(onPath).toRealPath()
                    .equals(repo.root().resolve("bin/dojo").toRealPath());
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static int doctor() {
        Repo repo = Repo.open();
        System.out.println("java    " + System.getProperty("java.version") + "  (" + System.getProperty("java.vendor") + ")");
        System.out.println("mvn     " + mvnVersion());
        System.out.println("仓库    " + repo.root());
        String onPath = dojoOnPath();
        if (onPath == null) {
            System.out.println("dojo    不在 PATH 上——在别的目录里敲 dojo 会找不到命令");
            Ui.info("挂上：ln -s " + repo.root().resolve("bin/dojo") + " ~/.local/bin/dojo");
        } else {
            System.out.println("dojo    " + onPath + (pointsHere(onPath, repo) ? "（指向本仓库）" : "（不是本仓库的 bin/dojo）"));
        }
        System.out.println("本仓库   " + repo.root().resolve("bin/dojo"));
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

    /**
     * 一个编译不过的文件会把整个模块挡住。错误发生在别的题上时，明确说是谁挡的、
     * 怎么清掉——否则用户会以为当前这道题坏了。
     */
    private static void foreignCompileFailures(Repo repo, String currentId, String output) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:^|\\s)/[^\\s:]*?\\.java(?=:\\[)").matcher(output);
        java.util.LinkedHashMap<String, String> problems = new java.util.LinkedHashMap<>();
        while (matcher.find()) {
            String file = matcher.group().strip();
            try {
                String id = repo.pathToId(Path.of(file)).orElse(null);
                if (id != null && !id.equals(currentId)) {
                    problems.putIfAbsent(id, file);
                }
            } catch (RuntimeException ignored) {
                // 路径不是本仓库的，忽略
            }
        }
        if (problems.isEmpty()) {
            return;
        }
        System.out.println();
        Ui.warn("挡住编译的是别的题（清掉之后当前题才跑得起来）：");
        int shown = 0;
        for (String id : problems.keySet()) {
            if (shown++ == 8) {
                System.out.println("  … 共 " + problems.size() + " 道");
                break;
            }
            System.out.println("  " + id + "   →  dojo test -p " + Naming.number(id) + " 看完整报错");
        }
        boolean looksSql = output.contains("非法字符: '#'") || output.contains("需要 class、interface、enum 或 record");
        if (looksSql) {
            Ui.info("多半是 SQL / Shell 题：没有 Java 模板，留在 solutions 里只会挡住所有题。");
        } else {
            Ui.info("这是本仓库里的文件：修好它，或单独 `dojo test -p <题号>` 看完整报错。");
            Ui.info("确实不要这道题了，再 `dojo clean <题号>`（会进回收站，可恢复）。");
        }
    }

    /** 上次测试日志里的编译错误属于哪道题（不是当前题才返回）；找不到返回 null。 */
    private static String foreignErrorOwner(Repo repo, String currentId, String output) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:^|\\s)(/[^\\s:]*?\\.java)(?=:\\[)").matcher(output);
        while (matcher.find()) {
            try {
                String id = repo.pathToId(Path.of(matcher.group(1))).orElse(null);
                if (id != null && !id.equals(currentId)) {
                    return id;
                }
            } catch (RuntimeException ignored) {
                // 路径不是本仓库的，忽略
            }
        }
        return null;
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
