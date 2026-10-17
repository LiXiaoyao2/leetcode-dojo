package dojo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** 评测集里的一道题：题面、卡住时的代码、预先算好的官方样例测试。 */
record EvalCase(String id, String slug, String title, String difficulty, String pkg,
                String testClass, String statement, String stuckCode, String testSource, String note) {

    static Path casesDir(Path repoRoot) {
        return repoRoot.resolve("eval/cases");
    }

    /** 读 eval/cases/*；limit > 0 时只取前 limit 道（按题号排序）。 */
    static List<EvalCase> loadAll(Path repoRoot, int limit) {
        Path dir = casesDir(repoRoot);
        if (!Files.isDirectory(dir)) {
            throw new DojoException("还没有评测集。先跑 `dojo eval --snapshot` 拉题（需要 eval/benchmark.txt 与 eval/stuck/*.java）。");
        }
        List<EvalCase> cases = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path child : stream.filter(Files::isDirectory).sorted().toList()) {
                cases.add(load(child));
            }
        } catch (IOException e) {
            throw new DojoException("读 " + dir + " 失败：" + e.getMessage(), e);
        }
        cases.sort(Comparator.comparing(EvalCase::id));
        if (limit > 0 && cases.size() > limit) {
            return List.copyOf(cases.subList(0, limit));
        }
        return List.copyOf(cases);
    }

    static EvalCase load(Path dir) {
        try {
            String json = Files.readString(dir.resolve("case.json"), StandardCharsets.UTF_8);
            Map<String, Object> node = Json.obj(Json.parse(json));
            String id = Json.strAt(node, "id");
            String notePath = dir.resolve("note.md").toString();
            String note = Files.isRegularFile(Path.of(notePath)) ? Files.readString(Path.of(notePath)) : "";
            return new EvalCase(
                    id,
                    Json.strAt(node, "slug"),
                    Json.strAt(node, "title"),
                    Json.strAt(node, "difficulty"),
                    Json.strAt(node, "pkg"),
                    Json.strAt(node, "testClass"),
                    Files.readString(dir.resolve("statement.md"), StandardCharsets.UTF_8),
                    Files.readString(dir.resolve("stuck.java"), StandardCharsets.UTF_8),
                    Files.readString(dir.resolve("Test.java"), StandardCharsets.UTF_8),
                    note);
        } catch (IOException e) {
            throw new DojoException("读 " + dir + " 失败：" + e.getMessage(), e);
        }
    }

    String label() {
        return id + " " + title;
    }

    static void write(Path dir, String name, String content) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
