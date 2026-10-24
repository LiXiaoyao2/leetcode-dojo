package dojo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 仓库布局的唯一知情者。所有路径都从 id 推导，不存映射表、不扫数据库。
 *
 * <pre>
 * problems/0001-two-sum/problem.md      题目与官方样例
 * problems/0001-two-sum/hints.md        已给过的提示（档位状态就藏在这里）
 * solutions/src/main/java/p0001_two_sum/Solution.java
 * solutions/src/test/java/p0001_two_sum/TwoSumTest.java
 * </pre>
 */
public final class Repo {

    private static final List<String> MARKERS = List.of("pom.xml", "problems", "solutions", "tool");

    private final Path root;

    private Repo(Path root) {
        this.root = root;
    }

    /** 只给测试用：直接指一个已经像仓库的目录，跳过向上查找。 */
    static Repo at(Path root) {
        return new Repo(root.toAbsolutePath().normalize());
    }

    public static Repo open() {
        String env = System.getenv("DOJO_HOME");
        if (env != null && !env.isBlank()) {
            Path path = Path.of(env).toAbsolutePath().normalize();
            if (looksLikeRoot(path)) {
                return new Repo(path);
            }
        }
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && current != null; i++) {
            if (looksLikeRoot(current)) {
                return new Repo(current);
            }
            current = current.getParent();
        }
        throw new DojoException("""
                当前目录不在 dojo 仓库里（找不到 pom.xml + problems/ + solutions/）。
                进入仓库根目录再运行，或设置 DOJO_HOME 指向仓库根目录。""");
    }

    private static boolean looksLikeRoot(Path path) {
        return MARKERS.stream().allMatch(marker -> Files.exists(path.resolve(marker)));
    }

    public Path root() {
        return root;
    }

    public Path problemsDir() {
        return root.resolve("problems");
    }

    public Path solutionsDir() {
        return root.resolve("solutions");
    }

    public List<String> problemIds() {
        if (!Files.isDirectory(problemsDir())) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(problemsDir())) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path problemDir(String id) {
        return problemsDir().resolve(id);
    }

    public Path problemMd(String id) {
        return problemDir(id).resolve("problem.md");
    }

    public Path hintsMd(String id) {
        return problemDir(id).resolve("hints.md");
    }

    public Path doneMarker(String id) {
        return problemDir(id).resolve("done");
    }

    public String packageOf(String id) {
        return Naming.packageOf(id);
    }

    public Path solutionFile(String id) {
        return solutionsDir().resolve("src/main/java")
                .resolve(packageOf(id).replace('.', '/'))
                .resolve("Solution.java");
    }

    /** 上次要提示时那份代码的本地快照；只用于算 diff，不进 git。 */
    public Path snapshotFile(String id) {
        return root().resolve(".dojo/snapshots").resolve(id + ".java");
    }

    public String testClass(String id) {
        return Naming.testClass(id);
    }

    public Path testFile(String id) {
        return solutionsDir().resolve("src/test/java")
                .resolve(packageOf(id).replace('.', '/'))
                .resolve(testClass(id) + ".java");
    }

    /** 用户输入 slug / URL / 题号 / 完整 id 都能认出来。 */
    public Optional<String> find(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String wanted = Naming.slugOf(input.strip());
        List<String> ids = problemIds();
        for (String id : ids) {
            if (id.equals(input.strip()) || Naming.slug(id).equals(wanted)) {
                return Optional.of(id);
            }
        }
        if (wanted.matches("\\d+")) {
            String number = String.format("%04d", Integer.parseInt(wanted));
            for (String id : ids) {
                if (Naming.number(id).equals(number)) {
                    return Optional.of(id);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<String> byPackage(String pkg) {
        for (String id : problemIds()) {
            if (packageOf(id).equals(pkg)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    /** 从文件路径反推题号：路径里带 problems/<id> 或 pNNNN_xxx/ 都算。 */
    public Optional<String> pathToId(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        for (String id : problemIds()) {
            if (absolute.startsWith(problemDir(id))) {
                return Optional.of(id);
            }
            String pkg = packageOf(id);
            for (int i = 0; i < absolute.getNameCount(); i++) {
                if (absolute.getName(i).toString().equals(pkg)) {
                    return Optional.of(id);
                }
            }
        }
        return Optional.empty();
    }

    /** 最近改过的解法文件 → 题号；文件系统就是「我上次在写哪题」的日志。 */
    public Optional<String> newestSolution() {
        Path base = solutionsDir().resolve("src/main/java");
        if (!Files.isDirectory(base)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.walk(base, 4)) {
            Optional<Path> newest = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !isKit(path))
                    .max(Comparator.comparing(path -> lastModified(path)));
            return newest.flatMap(path -> pathToId(path.getParent()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** kit 里是共用工具类，不是某道题的解法；分隔符要跟平台走。 */
    private static boolean isKit(Path path) {
        for (Path part : path) {
            if ("kit".equals(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    /** --problem 优先；否则按 cwd 推断；再否则取最近改过的解法。 */
    public String resolve(String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return find(explicit).orElseThrow(() -> new DojoException(
                    "本地没有 " + explicit + "。先用 `dojo new " + Naming.slugOf(explicit) + "` 拉题。"));
        }
        Optional<String> fromCwd = pathToId(Path.of("").toAbsolutePath());
        if (fromCwd.isPresent()) {
            return fromCwd.get();
        }
        return newestSolution().orElseThrow(() -> new DojoException("""
                不知道你指的是哪道题。
                  · 在题目目录/包目录里运行，或
                  · 用 --problem <id|slug> 指定，或
                  · 先 `dojo new two-sum` 拉一道题。"""));
    }

    /** 已完成标记与 problem.md 的读取。 */
    public String title(String id) {
        Path md = problemMd(id);
        if (!Files.isRegularFile(md)) {
            return id;
        }
        try {
            for (String line : Files.readAllLines(md)) {
                if (line.startsWith("# ")) {
                    return line.substring(2).strip();
                }
            }
        } catch (IOException e) {
            return id;
        }
        return id;
    }

    public boolean isDone(String id) {
        return Files.exists(doneMarker(id));
    }
}
