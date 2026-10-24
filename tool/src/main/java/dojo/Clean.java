package dojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 清理。构建产物（target/、上次测试日志）直接删，删了会自动重建；
 * 题目相关的东西只有一个去处：系统回收站。删错了从文件管理器里拖回来。
 */
public final class Clean {

    public record Freed(long bytes, List<Path> paths) {
    }

    private Clean() {
    }

    /** 两个模块的 target/ 与 .dojo/last-test.log。返回释放的字节数与删掉的东西。 */
    public static Freed buildArtifacts(Repo repo) {
        List<Path> removed = new ArrayList<>();
        long bytes = 0;
        for (String module : List.of("tool", "solutions")) {
            Path target = repo.root().resolve(module).resolve("target");
            bytes += sizeOf(target);
            if (deleteIfPresent(target)) {
                removed.add(target);
            }
        }
        Path log = repo.root().resolve(".dojo/last-test.log");
        bytes += sizeOf(log);
        if (deleteIfPresent(log)) {
            removed.add(log);
        }
        return new Freed(bytes, removed);
    }

    /** 一道题散落的三个地方：题面目录、解法包目录、测试包目录（后两个可能已被你删掉）。 */
    public static List<Path> pathsOf(Repo repo, String id) {
        String pkg = repo.packageOf(id);
        return Stream.of(
                        repo.problemDir(id),
                        repo.solutionsDir().resolve("src/main/java").resolve(pkg),
                        repo.solutionsDir().resolve("src/test/java").resolve(pkg))
                .filter(Files::exists)
                .toList();
    }

    /** 指定文件直接挪进系统回收站（用于清掉编译不过的生成测试）。 */
    public static void trashFiles(List<Path> paths) {
        trashPaths(paths);
    }

    /** 若干道题的文件，一次挪进系统回收站。返回挪走的路径。 */
    public static List<Path> trash(Repo repo, List<String> ids) {
        List<Path> paths = new ArrayList<>();
        for (String id : ids) {
            paths.addAll(pathsOf(repo, id));
        }
        if (paths.isEmpty()) {
            throw new DojoException("本地没有这些题的痕迹，没什么可删的。");
        }
        trashPaths(paths);
        return paths;
    }

    /** 走系统回收站：Windows 用 PowerShell，Unix 优先 mavis-trash，其次 gio，再其次 trash-put。 */
    static void trashPaths(List<Path> paths) {
        List<Path> unique = new ArrayList<>(new java.util.LinkedHashSet<>(paths));
        List<String> command;
        Path script = null;
        if (Platform.isWindows()) {
            script = writeWindowsTrashScript();
            command = List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.toString());
        } else {
            command = trashCommand();
        }
        List<String> argv = new ArrayList<>(command);
        for (Path path : unique) {
            argv.add(path.toString());
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(argv).redirectErrorStream(true);
            try {
                Process process = builder.start();
                String output = new String(process.getInputStream().readAllBytes()).strip();
                int exit = process.waitFor();
                // mavis-trash 偶尔搬完还退 1（对重复/已消失的路径报错），以最终结果为准。
                boolean allGone = unique.stream().noneMatch(Files::exists);
                if (exit != 0 && !allGone) {
                    throw new DojoException("移入回收站失败（" + command.get(0) + " 退出码 " + exit + "）"
                            + (output.isEmpty() ? "。" : "：" + output));
                }
            } catch (IOException e) {
                throw new DojoException("启动不了 " + command.get(0) + "：" + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DojoException("移入回收站被中断。");
            }
        } finally {
            if (script != null) {
                try {
                    Files.deleteIfExists(script);
                } catch (IOException ignored) {
                    // 临时脚本删不掉不影响结果
                }
            }
        }
    }

    /** Windows 没有 trash-cli，用 VisualBasic 的 API 把文件送进回收站。 */
    private static Path writeWindowsTrashScript() {
        String body = """
                param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Paths)
                Add-Type -AssemblyName Microsoft.VisualBasic
                foreach ($p in $Paths) {
                    if (Test-Path -LiteralPath $p) {
                        $item = Get-Item -LiteralPath $p
                        if ($item.PSIsContainer) {
                            [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($p, 'OnlyErrorDialogs', 'SendToRecycleBin')
                        } else {
                            [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($p, 'OnlyErrorDialogs', 'SendToRecycleBin')
                        }
                    }
                }
                """;
        try {
            Path script = Files.createTempFile("dojo-trash", ".ps1");
            Files.writeString(script, body, java.nio.charset.StandardCharsets.UTF_8);
            return script;
        } catch (IOException e) {
            throw new DojoException("写回收站脚本失败：" + e.getMessage());
        }
    }

    static List<String> trashCommand() {
        for (List<String> candidate : List.of(List.of("mavis-trash"), List.of("gio", "trash"), List.of("trash-put"))) {
            if (findExecutable(candidate.get(0)) != null) {
                return candidate;
            }
        }
        throw new DojoException("""
                找不到可用的回收站工具（mavis-trash / gio / trash-put 都没有）。
                装一个再试：trash-cli（trash-put），或直接手动删 problems/<题号> 与对应的 solutions 包。""");
    }

    /** PATH 里找可执行文件；找不到返回 null。 */
    static Path findExecutable(String name) {
        return Platform.findExecutable(name);
    }

    public static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    static long sizeOf(Path path) {
        if (!Files.exists(path)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(path)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> {
                try {
                    return Files.size(file);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private static boolean deleteIfPresent(Path path) {
        if (!Files.exists(path)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path item : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(item);
            }
        } catch (IOException e) {
            throw new DojoException("删不掉 " + path + "：" + e.getMessage());
        }
        return true;
    }
}
