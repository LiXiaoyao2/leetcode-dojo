package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** `dojo eval`：把评测集跑成一张能写进 README 的表。 */
final class Eval {

    record Result(EvalCase evalCase, String arm, String armLabel, List<EvalHint> hints,
                  List<EvalMetrics.Cell> cells, List<EvalJudge.Verdict> verdicts,
                  boolean l5Pass, String l5Verdict, long l5Millis) {
    }

    record Meta(String startedAt, String model, String judgeModel, String commit, int caseCount,
                List<String> arms, long millis) {
    }

    private Eval() {
    }

    static int run(String[] args) {
        boolean snapshot = false;
        boolean warmup = false;
        boolean judge = true;
        boolean refresh = false;
        int limit = 0;
        int jobs = 4;
        List<String> arms = List.of("ladder", "naive", "twopass");
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--snapshot" -> snapshot = true;
                case "--warmup" -> warmup = true;
                case "--no-judge" -> judge = false;
                case "--refresh" -> refresh = true;
                case "--cases" -> limit = Integer.parseInt(value(args, ++i, "--cases"));
                case "--jobs" -> jobs = Math.max(1, Integer.parseInt(value(args, ++i, "--jobs")));
                case "--arms" -> arms = List.of(value(args, ++i, "--arms").split(","));
                default -> throw new DojoException("eval 不认识的参数：" + arg);
            }
        }

        Repo repo = Repo.open();
        if (snapshot) {
            EvalSnapshot.sync(repo, refresh);
            return 0;
        }

        List<EvalCase> cases = EvalCase.loadAll(repo.root(), limit);
        if (cases.isEmpty()) {
            throw new DojoException("评测集是空的。先跑 `dojo eval --snapshot`。");
        }
        Config.Gateway gateway = Config.load(repo);
        Ai ai = new Ai(gateway);
        String judgeModel = System.getenv().getOrDefault("DOJO_JUDGE_MODEL", gateway.model());
        Config.Gateway judgeGateway = new Config.Gateway(gateway.baseUrl(), gateway.token(), judgeModel,
                gateway.provider(), gateway.source());
        Ai judgeAi = new Ai(judgeGateway);

        Ui.title("评测 " + cases.size() + " 道题 × " + arms.size() + " 种架构 × 5 档");
        Ui.info("模型 " + gateway.model() + " · 判官 " + judgeModel + " · 并发 " + jobs);
        long started = System.currentTimeMillis();

        Ui.step("先让每题跑一次卡住代码，拿到真实的失败输出…");
        warmUp(repo, cases, jobs, refresh);
        if (warmup) {
            return 0;
        }

        boolean useJudge = judge;
        List<Result> results = new ArrayList<>();
        List<EvalArms.Arm> selected = new ArrayList<>();
        for (String armName : arms) {
            selected.add(pick(ai, armName));
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(jobs, selected.size() * cases.size()));
        try {
            List<Callable<Result>> tasks = new ArrayList<>();
            for (EvalArms.Arm arm : selected) {
                for (EvalCase evalCase : cases) {
                    tasks.add(() -> runOne(repo, evalCase, arm, ai, judgeAi, useJudge));
                }
            }
            for (Future<Result> future : pool.invokeAll(tasks)) {
                Result result = future.get();
                if (result != null) {
                    results.add(result);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DojoException("评测被打断", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new DojoException("评测失败：" + e.getCause().getMessage(), e.getCause());
        } finally {
            pool.shutdownNow();
        }

        long millis = System.currentTimeMillis() - started;
        Meta meta = new Meta(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                gateway.model(), judgeModel, commit(repo), cases.size(), arms, millis);
        writeRaw(repo, meta, results);
        EvalReport.write(repo, meta, results);
        EvalReport.console(meta, results);
        return 0;
    }

    private static EvalArms.Arm pick(Ai ai, String name) {
        for (EvalArms.Arm arm : EvalArms.all(ai)) {
            if (arm.name().equals(name)) {
                return arm;
            }
        }
        throw new DojoException("不认识的架构：" + name + "（可选 ladder / naive / twopass）");
    }

    private static void warmUp(Repo repo, List<EvalCase> cases, int jobs, boolean refresh) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(jobs, cases.size()));
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (EvalCase evalCase : cases) {
                tasks.add(() -> {
                    EvalWorkspace workspace = EvalWorkspace.prepare(repo, evalCase, "setup", refresh);
                    String log = workspace.testLog();
                    System.out.println("  · " + evalCase.id() + "：" + log.lines().findFirst().orElse(""));
                    System.out.println(log.lines().skip(1).limit(6).map(line -> "      " + line)
                            .reduce("", (a, b) -> a + b + "\n"));
                    return evalCase.id();
                });
            }
            for (Future<String> future : pool.invokeAll(tasks)) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException e) {
            throw new DojoException("准备卡住代码的测试输出失败：" + e.getCause().getMessage(), e.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    private static Result runOne(Repo repo, EvalCase evalCase, EvalArms.Arm arm, Ai ai,
                                 Ai judgeAi, boolean judge) {
        try {
            EvalWorkspace workspace = EvalWorkspace.prepare(repo, evalCase, arm.name(), false);
            String testLog = workspace.testLog();
            long start = System.currentTimeMillis();
            List<EvalHint> hints = arm.run(evalCase, testLog, ai);
            long millis = System.currentTimeMillis() - start;
            List<EvalMetrics.Cell> cells = EvalMetrics.of(hints);
            List<EvalJudge.Verdict> verdicts = judge ? EvalJudge.judge(judgeAi, evalCase, hints) : List.of();
            L5 l5 = checkL5(workspace, hints);
            synchronized (Eval.class) {
                System.out.println(String.format(Locale.ROOT, "  [%-8s] %s · %.1fs%s",
                        arm.name(), evalCase.id(), millis / 1000.0, l5.pass() ? " · L5 跑绿" : ""));
            }
            return new Result(evalCase, arm.name(), arm.label(), hints, cells, verdicts,
                    l5.pass(), l5.verdict(), l5.millis());
        } catch (DojoException e) {
            synchronized (Eval.class) {
                System.out.println("  [" + arm.name() + "] " + evalCase.id() + " 失败：" + e.getMessage());
            }
            return null;
        }
    }

    record L5(boolean pass, String verdict, long millis) {
    }

    private static L5 checkL5(EvalWorkspace workspace, List<EvalHint> hints) {
        EvalHint last = hints.isEmpty() ? null : hints.get(hints.size() - 1);
        if (last == null || last.level() != 5 || last.failed()) {
            return new L5(false, "第 5 档没有产出", 0);
        }
        String code = EvalWorkspace.extractJava(last.text());
        if (code == null) {
            return new L5(false, "第 5 档没有 Java 代码", 0);
        }
        workspace.writeSolution(code);
        EvalWorkspace.Run run = workspace.runTests();
        return new L5(run.ok(), run.verdict(), run.millis());
    }

    private static String value(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new DojoException(flag + " 后面要跟一个值");
        }
        return args[index];
    }

    private static String commit(Repo repo) {
        try {
            java.nio.file.Path git = Platform.findExecutable("git");
            if (git == null) {
                return "?";
            }
            Process process = new ProcessBuilder(git.toString(), "rev-parse", "--short", "HEAD")
                    .directory(repo.root().toFile()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            process.waitFor();
            return output.isEmpty() ? "?" : output;
        } catch (IOException | InterruptedException e) {
            return "?";
        }
    }

    private static void writeRaw(Repo repo, Meta meta, List<Result> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"meta\": {");
        sb.append("\"startedAt\": ").append(Json.quote(meta.startedAt())).append(", ");
        sb.append("\"model\": ").append(Json.quote(meta.model())).append(", ");
        sb.append("\"judgeModel\": ").append(Json.quote(meta.judgeModel())).append(", ");
        sb.append("\"commit\": ").append(Json.quote(meta.commit())).append(", ");
        sb.append("\"cases\": ").append(meta.caseCount()).append(", ");
        sb.append("\"millis\": ").append(meta.millis()).append("},\n  \"results\": [\n");
        for (int i = 0; i < results.size(); i++) {
            Result result = results.get(i);
            sb.append("    {\"case\": ").append(Json.quote(result.evalCase().id()))
                    .append(", \"title\": ").append(Json.quote(result.evalCase().title()))
                    .append(", \"difficulty\": ").append(Json.quote(result.evalCase().difficulty()))
                    .append(", \"arm\": ").append(Json.quote(result.arm()))
                    .append(", \"l5\": {\"pass\": ").append(result.l5Pass())
                    .append(", \"verdict\": ").append(Json.quote(result.l5Verdict()))
                    .append(", \"millis\": ").append(result.l5Millis()).append("},\n     \"hints\": [");
            for (int j = 0; j < result.cells().size(); j++) {
                EvalMetrics.Cell cell = result.cells().get(j);
                EvalJudge.Verdict verdict = verdictAt(result.verdicts(), cell.level());
                if (j > 0) {
                    sb.append(", ");
                }
                sb.append("{\"level\": ").append(cell.level())
                        .append(", \"chars\": ").append(cell.chars())
                        .append(", \"millis\": ").append(cell.millis())
                        .append(", \"tokens\": ").append(cell.tokens())
                        .append(", \"leak\": ").append(cell.leak())
                        .append(", \"repeat\": ").append(cell.repeat())
                        .append(", \"tooLong\": ").append(cell.tooLong())
                        .append(", \"truncated\": ").append(cell.truncated())
                        .append(", \"failed\": ").append(cell.failed())
                        .append(", \"score\": ").append(verdict == null ? 0 : verdict.score())
                        .append(", \"heuristic\": ").append(verdict != null && verdict.heuristic())
                        .append(", \"actionable\": ").append(verdict != null && verdict.actionable())
                        .append(", \"reason\": ").append(Json.quote(verdict == null ? "" : verdict.reason()))
                        .append(", \"text\": ").append(Json.quote(cell.text())).append('}');
            }
            sb.append("]}");
            if (i < results.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  ]\n}\n");
        Path file = repo.root().resolve("eval/raw.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DojoException("写 " + file + " 失败：" + e.getMessage(), e);
        }
    }


    record Raw(Meta meta, List<Result> results) {
    }

    /** 只重建报告：从 eval/raw.json 读回结果，不调用模型。改排版时省一轮全量。 */
    static int reportFromRaw(Repo repo) {
        Raw raw = readRaw(repo);
        EvalReport.write(repo, raw.meta(), raw.results());
        EvalReport.console(raw.meta(), raw.results(), false);
        return 0;
    }

    static Raw readRaw(Repo repo) {
        Path file = repo.root().resolve("eval/raw.json");
        if (!Files.exists(file)) {
            throw new DojoException("还没有 " + rootRelative(repo, file) + "，先跑一次 dojo eval。");
        }
        try {
            Map<String, Object> root = Json.obj(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
            Map<String, Object> metaMap = Json.obj(root.get("meta"));
            List<Result> results = new ArrayList<>();
            for (Object rawResult : Json.arr(root.get("results"))) {
                Map<String, Object> r = Json.obj(rawResult);
                String id = text(r, "case");
                EvalCase evalCase = new EvalCase(id, Naming.slug(id), text(r, "title"),
                        text(r, "difficulty"), Naming.packageOf(id), Naming.testClass(id),
                        "", "", "", "");
                List<EvalHint> hints = new ArrayList<>();
                List<EvalMetrics.Cell> cells = new ArrayList<>();
                List<EvalJudge.Verdict> verdicts = new ArrayList<>();
                for (Object rawHint : Json.arr(r.get("hints"))) {
                    Map<String, Object> h = Json.obj(rawHint);
                    int level = (int) number(h, "level");
                    String hintText = text(h, "text");
                    boolean failed = flag(h, "failed");
                    long millis = number(h, "millis");
                    int tokens = (int) number(h, "tokens");
                    boolean leak = flag(h, "leak");
                    boolean repeat = flag(h, "repeat");
                    boolean tooLong = flag(h, "tooLong");
                    boolean truncated = flag(h, "truncated");
                    hints.add(new EvalHint(level, hintText, millis, 0, tokens, truncated, leak, failed ? "失败" : null));
                    cells.add(new EvalMetrics.Cell(level, hintText, hintText == null ? 0 : hintText.length(),
                            leak, repeat, tooLong, millis, tokens, failed, truncated));
                    if (!failed) {
                        boolean actionable = h.containsKey("actionable")
                                ? flag(h, "actionable")
                                : EvalJudge.heuristic(hints.get(hints.size() - 1), hints.subList(0, hints.size() - 1)).actionable();
                        verdicts.add(new EvalJudge.Verdict(level, (int) number(h, "score"), leak, repeat,
                                actionable, tooLong, flag(h, "heuristic"),
                                text(h, "reason") + (h.containsKey("actionable") ? "" : "（可行动由规则补）")));
                    }
                }
                Map<String, Object> l5 = Json.obj(r.get("l5"));
                results.add(new Result(evalCase, text(r, "arm"), EvalArms.labelOf(text(r, "arm")),
                        hints, cells, verdicts, flag(l5, "pass"), text(l5, "verdict"), number(l5, "millis")));
            }
            Meta meta = new Meta(text(metaMap, "startedAt"), text(metaMap, "model"),
                    text(metaMap, "judgeModel"), text(metaMap, "commit"),
                    (int) number(metaMap, "cases"),
                    results.stream().map(Result::arm).distinct().toList(),
                    number(metaMap, "millis"));
            return new Raw(meta, results);
        } catch (IOException e) {
            throw new DojoException("读 " + file + " 失败：" + e.getMessage(), e);
        }
    }

    private static String rootRelative(Repo repo, Path file) {
        return repo.root().relativize(file).toString();
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number n ? n.longValue() : 0;
    }

    private static boolean flag(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Boolean b && b;
    }


    /** 判官重测：同一份答案评两次，看分差有多大。用来给 L5 主观分标噪声。 */
    static int noise(Repo repo, int samples) {
        Raw raw = readRaw(repo);
        Ai ai = new Ai(Config.load(repo));
        int n = 0;
        int differ = 0;
        int big = 0;
        int done = 0;
        Ui.title("判官重测同一份答案");
        for (Result result : raw.results()) {
            EvalHint l5 = result.hints().stream().filter(h -> h.level() == 5).findFirst().orElse(null);
            if (l5 == null || l5.failed()) {
                continue;
            }
            if (n++ >= samples) {
                break;
            }
            int first = judgeOnce(ai, result.evalCase(), l5);
            int second = judgeOnce(ai, result.evalCase(), l5);
            done++;
            if (first != second) {
                differ++;
            }
            if (Math.abs(first - second) >= 2) {
                big++;
            }
            System.out.printf(Locale.ROOT, "  %-52s 第一次 %d · 第二次 %d%n",
                    result.evalCase().label(), first, second);
        }
        Ui.info("");
        Ui.info("重测 " + done + " 份 · 有分差 " + differ + " 份 · 分差 ≥2 的 " + big + " 份");
        return 0;
    }

    private static int judgeOnce(Ai ai, EvalCase evalCase, EvalHint hint) {
        List<EvalJudge.Verdict> verdicts = EvalJudge.judge(ai, evalCase, List.of(hint));
        return verdicts.isEmpty() ? 0 : verdicts.get(0).score();
    }

    static EvalJudge.Verdict verdictAt(List<EvalJudge.Verdict> verdicts, int level) {
        for (EvalJudge.Verdict verdict : verdicts) {
            if (verdict.level() == level) {
                return verdict;
            }
        }
        return null;
    }

    static String duration(long millis) {
        return Duration.ofMillis(millis).toMinutes() + " 分 " + (Duration.ofMillis(millis).toSeconds() % 60) + " 秒";
    }
}
