package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** 每题一个临时单模块 Maven 工程：把「卡住的代码 / 模型给的题解」丢进去跑同一份官方样例测试。 */
final class EvalWorkspace {

    /** 一次 mvn 运行的结果。 */
    record Run(int exit, String output, long millis) {
        boolean ok() {
            return exit == 0;
        }

        String verdict() {
            if (ok()) {
                return "通过";
            }
            if (output.contains("COMPILATION ERROR") || output.contains("Compilation failure")) {
                return "编译失败";
            }
            if (Pattern.compile("Errors: [1-9]").matcher(output).find()) {
                return "运行时异常";
            }
            if (Pattern.compile("Failures: [1-9]").matcher(output).find()) {
                return "断言失败";
            }
            return "构建失败";
        }
    }

    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```(?:java|Java)?\\s*\\n(.*?)```");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+[\\w.]+\\s*;\\s*\\n?");

    private final Repo repo;
    private final EvalCase evalCase;
    private final Path dir;
    private final Path cacheDir;

    private EvalWorkspace(Repo repo, EvalCase evalCase, Path dir, Path cacheDir) {
        this.repo = repo;
        this.evalCase = evalCase;
        this.dir = dir;
        this.cacheDir = cacheDir;
    }

    static EvalWorkspace prepare(Repo repo, EvalCase evalCase, String arm, boolean refresh) {
        Path base = repo.root().resolve(".dojo/eval-work").resolve(evalCase.id());
        Path dir = base.resolve(arm);
        try {
            Files.createDirectories(dir.resolve("src/main/java/" + evalCase.pkg()));
            Files.createDirectories(dir.resolve("src/test/java/" + evalCase.pkg()));
            Path pom = dir.resolve("pom.xml");
            Files.copy(repo.root().resolve("eval/template/pom.xml"), pom, StandardCopyOption.REPLACE_EXISTING);
            copyKit(repo, dir);
            Files.writeString(dir.resolve("src/test/java/" + evalCase.pkg() + "/" + evalCase.testClass() + ".java"),
                    evalCase.testSource(), StandardCharsets.UTF_8);
            Files.createDirectories(base);
            if (refresh) {
                Files.deleteIfExists(base.resolve("testlog-stuck.txt"));
            }
        } catch (IOException e) {
            throw new DojoException("准备评测工程 " + dir + " 失败：" + e.getMessage(), e);
        }
        return new EvalWorkspace(repo, evalCase, dir, base);
    }

    private static void copyKit(Repo repo, Path dir) throws IOException {
        Path kit = repo.root().resolve("solutions/src/main/java/kit");
        if (!Files.isDirectory(kit)) {
            throw new DojoException("找不到 " + kit + "，评测工程缺 kit 类型。");
        }
        Path target = dir.resolve("src/main/java/kit");
        Files.createDirectories(target);
        try (Stream<Path> stream = Files.list(kit)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                Files.copy(file, target.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    EvalCase evalCase() {
        return evalCase;
    }

    /** 卡住代码的真实失败输出（缓存；production 里 hint 读到的就是这种摘要）。 */
    String testLog() {
        Path cache = cacheDir.resolve("testlog-stuck.txt");
        if (Files.isRegularFile(cache)) {
            try {
                return Files.readString(cache, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new DojoException("读 " + cache + " 失败：" + e.getMessage(), e);
            }
        }
        writeSolution(evalCase.stuckCode());
        Run run = runTests();
        String summary = Mvn.summarize(new Mvn.Result(run.exit(), run.output(), run.millis(), true));
        String log = "# 卡住代码跑官方样例（exit " + run.exit() + " · " + run.verdict() + "）\n" + summary;
        try {
            Files.writeString(cache, log, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DojoException("写 " + cache + " 失败：" + e.getMessage(), e);
        }
        return log;
    }

    void writeSolution(String code) {
        String body = normalize(code);
        try {
            Files.writeString(dir.resolve("src/main/java/" + evalCase.pkg() + "/Solution.java"),
                    body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DojoException("写评测解法失败：" + e.getMessage(), e);
        }
    }

    /** 把 package 行统一成评测题的包名，其余原样。 */
    String normalize(String code) {
        String stripped = PACKAGE.matcher(code == null ? "" : code).replaceAll("");
        return "package " + evalCase.pkg() + ";\n\n"
                + "// 评测时统一补上 IDEA 模板里本来就有的导入，L5 只衡量解法本身\n"
                + "import java.util.*;\nimport kit.*;\n\n"
                + stripped.strip() + "\n";
    }

    Run runTests() {
        return exec(List.of(Platform.mvnCommand(), "-B", "-ntp", "test", "-Dtest=" + evalCase.testClass(),
                "-DfailIfNoTests=false"));
    }

    /** 从模型回复里抠出最后一段 Java 代码；抠不到返回 null。 */
    static String extractJava(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<String> blocks = new ArrayList<>();
        Matcher matcher = CODE_FENCE.matcher(text);
        while (matcher.find()) {
            String block = matcher.group(1).strip();
            if (block.contains("class ") || block.contains("interface ") || block.contains("enum ")) {
                blocks.add(block);
            }
        }
        if (!blocks.isEmpty()) {
            return blocks.get(blocks.size() - 1);
        }
        return Ladder.looksLikeJava(text) ? text.strip() : null;
    }

    private Run exec(List<String> command) {
        Path log = dir.resolve("run.log");
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile());
            long start = System.nanoTime();
            Process process = builder.start();
            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            long millis = (System.nanoTime() - start) / 1_000_000;
            if (!finished) {
                process.destroyForcibly();
                return new Run(124, "评测工程超时（300 s）", millis);
            }
            String output = Platform.readText(log);
            return new Run(process.exitValue(), output, millis);
        } catch (IOException e) {
            return new Run(127, "跑不了 mvn：" + e.getMessage(), 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DojoException("评测被打断", e);
        }
    }
}
