package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Maven 的唯一调用点：编译校验、跑单测、把输出存成提示的上下文。 */
public final class Mvn {

    public record Result(int exit, String output, long millis, boolean available) {

        public boolean ok() {
            return available && exit == 0;
        }
    }

    private Mvn() {
    }

    public static Result testCompile(Path root) {
        return run(root, List.of("test-compile"), 300);
    }

    public static Result test(Path root, String testClass) {
        return run(root, List.of("test", "-Dtest=" + testClass, "-DfailIfNoTests=false",
                "-DtrimStackTrace=false"), 300);
    }

    private static Result run(Path root, List<String> args, int timeoutSeconds) {
        List<String> command = new ArrayList<>(List.of("mvn", "-B", "-ntp", "-pl", "solutions"));
        command.addAll(args);
        long start = System.currentTimeMillis();
        Path log = null;
        try {
            log = Files.createTempFile("dojo-mvn", ".log");
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile());
            Process process = builder.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Result(124, "mvn 超时（" + timeoutSeconds + " 秒），已终止。", timeoutSeconds * 1000L, true);
            }
            String output = Files.readString(log, StandardCharsets.UTF_8);
            return new Result(process.exitValue(), output, System.currentTimeMillis() - start, true);
        } catch (IOException e) {
            return new Result(127, "找不到 mvn 或启动失败：" + e.getMessage(), 0, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(130, "mvn 被中断", 0, true);
        } finally {
            if (log != null) {
                try {
                    Files.deleteIfExists(log);
                } catch (IOException ignored) {
                    // 临时文件删不掉不影响结果
                }
            }
        }
    }

    private static final List<String> NOISE = List.of(
            "Please refer to", "-> [Help", "Re-run Maven", "For more information",
            "Failed to execute goal", "dumpstream", "surefire-reports", "Help 1",
            "To see the full stack trace");

    /** 只保留能看懂的部分：失败摘要、断言差异、编译错误；Maven 的套话一律丢掉。 */
    public static String summarize(Result result) {
        if (!result.available()) {
            return result.output();
        }
        LinkedHashSet<String> keep = new LinkedHashSet<>();
        for (String line : result.output().split("\n")) {
            String s = line.strip();
            if (s.isEmpty() || s.equals("[ERROR]") || NOISE.stream().anyMatch(s::contains)) {
                continue;
            }
            if (s.contains("Tests run:")
                    || s.startsWith("[ERROR]")
                    || s.startsWith("ERROR")
                    || s.contains("AssertionFailedError")
                    || s.contains("expected:")
                    || s.contains("but was:")
                    || s.contains("BUILD FAILURE")
                    || s.contains(".java:[")) {
                keep.add(s.length() > 220 ? s.substring(0, 220) + "…" : s);
            }
        }
        if (keep.isEmpty()) {
            String tail = tail(result.output(), 12);
            return tail.isBlank() ? "（mvn 没有输出）" : tail;
        }
        List<String> lines = new ArrayList<>(keep);
        return String.join("\n", lines.subList(0, Math.min(lines.size(), 30)));
    }

    public static String tail(String text, int lines) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] all = text.strip().split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, all.length - lines); i < all.length; i++) {
            sb.append(all[i].strip()).append('\n');
        }
        return sb.toString().strip();
    }

    public static void saveLastTest(Repo repo, String id, String testClass, Result result) {
        StringBuilder sb = new StringBuilder();
        sb.append("problem=").append(id).append('\n');
        sb.append("class=").append(testClass).append('\n');
        sb.append("time=").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append('\n');
        sb.append("exit=").append(result.exit()).append('\n');
        sb.append("---\n");
        sb.append(summarize(result));
        try {
            Path file = repo.root().resolve(".dojo/last-test.log");
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Ui.warn("写 .dojo/last-test.log 失败：" + e.getMessage());
        }
    }

    /** 代码改过、日志还是旧的，就该先重跑一次测试再要提示。 */
    public static boolean stale(Repo repo, String id, java.nio.file.Path code) {
        if (code == null || !Files.isRegularFile(code)) {
            return false;
        }
        Path log = repo.root().resolve(".dojo/last-test.log");
        if (!Files.isRegularFile(log)) {
            return true;
        }
        try {
            String text = Files.readString(log, StandardCharsets.UTF_8);
            int marker = text.indexOf("---\n");
            String header = marker < 0 ? text : text.substring(0, marker);
            if (!header.contains("problem=" + id + "\n")) {
                return true;
            }
            return Files.getLastModifiedTime(code).toMillis() > Files.getLastModifiedTime(log).toMillis();
        } catch (IOException e) {
            return true;
        }
    }

    /** 只有「最近一次跑的就是这道题」才把日志交给模型，避免张冠李戴。 */
    public static String lastTestFor(Repo repo, String id) {
        Path file = repo.root().resolve(".dojo/last-test.log");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            int marker = text.indexOf("---\n");
            if (marker < 0) {
                return null;
            }
            String header = text.substring(0, marker);
            if (!header.contains("problem=" + id + "\n")) {
                return null;
            }
            return text.substring(marker + 4);
        } catch (IOException e) {
            return null;
        }
    }
}
