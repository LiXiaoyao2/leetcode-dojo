package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** `dojo eval --snapshot`：把 benchmark.txt 里的题拉成离线评测集（题面 + 卡住代码 + 官方样例测试）。 */
final class EvalSnapshot {

    private EvalSnapshot() {
    }

    static void sync(Repo repo, boolean refresh) {
        Path root = repo.root();
        Path benchmark = root.resolve("eval/benchmark.txt");
        if (!Files.isRegularFile(benchmark)) {
            throw new DojoException("缺 " + benchmark + "：一行一个 LeetCode slug。");
        }
        List<String> slugs;
        try {
            slugs = Files.readAllLines(benchmark, StandardCharsets.UTF_8).stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            throw new DojoException("读 " + benchmark + " 失败：" + e.getMessage(), e);
        }
        if (slugs.isEmpty()) {
            throw new DojoException(benchmark + " 是空的。");
        }

        int made = 0;
        int skipped = 0;
        for (int i = 0; i < slugs.size(); i++) {
            String slug = slugs.get(i);
            Ui.step(String.format("[%2d/%2d] %s", i + 1, slugs.size(), slug));
            Problem problem = LeetCode.fetch(slug);
            String id = problem.id();
            Path dir = EvalCase.casesDir(root).resolve(id);
            if (!refresh && Files.isRegularFile(dir.resolve("case.json"))) {
                Ui.info("已有，跳过（--refresh 重写）");
                skipped++;
                continue;
            }
            String pkg = Naming.packageOf(id);
            String testClass = Naming.testClass(id);
            TestGen.Generated generated = TestGen.generate(problem, pkg, testClass, false);
            if (generated.source() == null) {
                Ui.warn("跳过 " + slug + "：" + generated.note());
                continue;
            }
            String stuck = stuckCode(root, slug, problem);
            EvalCase.write(dir, "statement.md", problem.contentMd());
            EvalCase.write(dir, "stuck.java", stuck);
            EvalCase.write(dir, "Test.java", generated.source());
            EvalCase.write(dir, "case.json", caseJson(problem, pkg, testClass, generated.note()));
            made++;
            Ui.ok(id + " · " + problem.title() + " · 测试 " + testClass + ".java");
            if (i < slugs.size() - 1) {
                pause(300);
            }
        }
        Ui.ok("评测集就绪：" + made + " 道新写入，" + skipped + " 道已存在 → " + EvalCase.casesDir(root));
    }

    private static String caseJson(Problem problem, String pkg, String testClass, String note) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": ").append(Json.quote(problem.id())).append(",\n");
        sb.append("  \"slug\": ").append(Json.quote(problem.slug())).append(",\n");
        sb.append("  \"title\": ").append(Json.quote(problem.title())).append(",\n");
        sb.append("  \"difficulty\": ").append(Json.quote(problem.difficultyZh())).append(",\n");
        sb.append("  \"pkg\": ").append(Json.quote(pkg)).append(",\n");
        sb.append("  \"testClass\": ").append(Json.quote(testClass)).append(",\n");
        sb.append("  \"tags\": [");
        for (int i = 0; i < problem.tags().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(Json.quote(problem.tags().get(i)));
        }
        sb.append("],\n");
        sb.append("  \"generatorNote\": ").append(Json.quote(note == null ? "" : note)).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String stuckCode(Path root, String slug, Problem problem) {
        Path file = root.resolve("eval/stuck/" + slug + ".java");
        if (Files.isRegularFile(file)) {
            try {
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new DojoException("读 " + file + " 失败：" + e.getMessage(), e);
            }
        }
        Ui.warn("缺 eval/stuck/" + slug + ".java，先用空模板占位（评测结论会失真）");
        return "class Solution {\n    // TODO: 这里应该是一段「写到一半卡住」的真实代码\n}\n";
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
