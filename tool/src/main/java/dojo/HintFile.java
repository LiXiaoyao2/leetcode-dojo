package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 把这一档提示落成一个可直接在编辑器里读的 markdown，供 IDEA 快捷键使用。 */
public final class HintFile {

    private HintFile() {
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static Path path(Repo repo, String id) {
        return repo.problemDir(id).resolve("last-hint.md");
    }

    public static String render(String id, Ladder.Level level, String note, String text, LocalDateTime at) {
        String next = level.number() < 5
                ? "下一档：`dojo hint` → L" + (level.number() + 1) + " · " + Ladder.Level.of(level.number() + 1).name() + "\n"
                : "最后一档。去写，然后 `dojo test`。\n";
        return render(id, "L" + level.number() + " · " + level.name(), note, text, at, next);
    }

    public static String render(String id, String heading, String note, String text, LocalDateTime at,
                                String nextLine) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(heading).append("\n\n");
        sb.append("> ").append(id).append(" · ").append(at.format(STAMP));
        if (note != null && !note.isBlank()) {
            sb.append(" · 卡点：").append(note.strip().replace("\n", " "));
        }
        sb.append("\n\n");
        sb.append(text.strip()).append("\n\n");
        sb.append("---\n\n");
        sb.append(nextLine);
        return sb.toString();
    }

    public static Path write(Repo repo, String id, Ladder.Level level, String note, String text)
            throws IOException {
        return write(repo, id, render(id, level, note, text, LocalDateTime.now()));
    }

    public static Path write(Repo repo, String id, String heading, String note, String text, String nextLine)
            throws IOException {
        return write(repo, id, render(id, heading, note, text, LocalDateTime.now(), nextLine));
    }

    private static Path write(Repo repo, String id, String content) throws IOException {
        Path file = path(repo, id);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** 有 idea CLI 就交给它（会开在正在运行的 IDEA 里），否则退回系统默认程序。 */
    public static boolean openInIde(Path file) {
        if ("0".equals(System.getenv("DOJO_OPEN"))) {
            return false;
        }
        for (List<String> command : List.of(
                List.of("idea", file.toString()),
                List.of("idea.sh", file.toString()),
                List.of("xdg-open", file.toString()))) {
            String exe = command.get(0);
            if (which(exe) == null) {
                continue;
            }
            try {
                Process process = new ProcessBuilder(command)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                return process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0;
            } catch (IOException e) {
                // 换下一个
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static String which(String exe) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, exe);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }
}
