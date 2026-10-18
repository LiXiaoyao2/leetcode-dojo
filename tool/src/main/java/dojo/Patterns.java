package dojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * resources/patterns.md 的读取与渲染。
 *
 * <p>模型只被允许输出一个受控的模式 id，模板、题号、链接全部来自这张表——这样既不会出现
 * 幻觉 URL，也让「学习资料」可以被 review 和版本管理。
 */
public final class Patterns {

    public record Pattern(String id, String name, String essence, List<String> signals,
                          List<String> prereqs, List<String> skills, String snippet,
                          List<String> before, List<String> practice, String link,
                          String backup, List<String> pitfalls) {
    }

    /** 模型输出 PATTERN 时允许的枚举；与 patterns.md 的条目 id 对应。 */
    public static final List<String> IDS = List.of(
            "two-pointers", "sliding-window", "binary-search", "monotonic-stack", "monotonic-queue",
            "prefix-sum", "hash-map", "stack", "linked-list", "tree-traversal", "bst", "heap",
            "backtracking", "graph-traversal", "union-find", "topo-sort", "shortest-path", "trie",
            "dp-linear", "dp-knapsack", "dp-interval", "dp-bitmask", "bit-manipulation", "math");

    private static final String FILE = "resources/patterns.md";

    private static final Map<String, String> TAG_MAP = new LinkedHashMap<>();

    static {
        TAG_MAP.put("双指针", "two-pointers");
        TAG_MAP.put("滑动窗口", "sliding-window");
        TAG_MAP.put("二分查找", "binary-search");
        TAG_MAP.put("单调栈", "monotonic-stack");
        TAG_MAP.put("单调队列", "monotonic-queue");
        TAG_MAP.put("前缀和", "prefix-sum");
        TAG_MAP.put("哈希表", "hash-map");
        TAG_MAP.put("栈", "stack");
        TAG_MAP.put("链表", "linked-list");
        TAG_MAP.put("树", "tree-traversal");
        TAG_MAP.put("二叉树", "tree-traversal");
        TAG_MAP.put("二叉搜索树", "bst");
        TAG_MAP.put("堆（优先队列）", "heap");
        TAG_MAP.put("贪心", "heap");
        TAG_MAP.put("回溯", "backtracking");
        TAG_MAP.put("图", "graph-traversal");
        TAG_MAP.put("并查集", "union-find");
        TAG_MAP.put("拓扑排序", "topo-sort");
        TAG_MAP.put("最短路", "shortest-path");
        TAG_MAP.put("字典树", "trie");
        TAG_MAP.put("动态规划", "dp-linear");
        TAG_MAP.put("位运算", "bit-manipulation");
        TAG_MAP.put("数学", "math");
        TAG_MAP.put("计数", "math");
    }

    private Patterns() {
    }

    public static List<Pattern> load(Repo repo) {
        Path file = repo.root().resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<Pattern> patterns = new ArrayList<>();
        try {
            String id = null;
            String name = null;
            Map<String, String> fields = new LinkedHashMap<>();
            for (String raw : Files.readAllLines(file)) {
                String line = raw.strip();
                if (line.startsWith("## ")) {
                    if (id != null) {
                        patterns.add(build(id, name, fields));
                    }
                    String header = line.substring(3).strip();
                    int dot = header.indexOf(" · ");
                    id = dot < 0 ? header : header.substring(0, dot).strip();
                    name = dot < 0 ? header : header.substring(dot + 3).strip();
                    fields = new LinkedHashMap<>();
                } else if (id != null && line.startsWith("- ")) {
                    String body = line.substring(2).strip();
                    int colon = colon(body);
                    if (colon > 0) {
                        fields.put(body.substring(0, colon).strip(), body.substring(colon + 1).strip());
                    }
                }
            }
            if (id != null) {
                patterns.add(build(id, name, fields));
            }
        } catch (IOException e) {
            throw new DojoException("读不了 " + file + "：" + e.getMessage());
        }
        return patterns;
    }

    private static int colon(String line) {
        int full = line.indexOf('：');
        int half = line.indexOf(':');
        if (full < 0) {
            return half;
        }
        return half < 0 ? full : Math.min(full, half);
    }

    private static Pattern build(String id, String name, Map<String, String> fields) {
        return new Pattern(
                id,
                name == null ? id : name,
                fields.getOrDefault("本质", ""),
                split(fields.get("信号"), " / "),
                split(fields.get("前置"), ","),
                split(fields.get("子技能"), " / "),
                fields.getOrDefault("模板", ""),
                split(fields.get("前置题"), ","),
                split(fields.get("巩固题"), ","),
                first(fields.get("链接")),
                second(fields.get("链接")),
                pitfalls(fields.get("易错")));
    }

    private static List<String> pitfalls(String value) {
        List<String> parts = split(value, "；");
        return parts.size() > 1 ? parts : split(value, " / ");
    }

    private static List<String> split(String value, String separator) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : value.split(java.util.regex.Pattern.quote(separator))) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    private static String first(String links) {
        List<String> parts = split(links, "|");
        return parts.isEmpty() ? "" : parts.get(0);
    }

    private static String second(String links) {
        List<String> parts = split(links, "|");
        return parts.size() < 2 ? "" : parts.get(1);
    }

    public static Optional<Pattern> find(Repo repo, String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String wanted = id.strip().toLowerCase();
        return load(repo).stream().filter(p -> p.id().equals(wanted)).findFirst();
    }

    /** 给 L4 提示词用：模型只能从这些 id 里选。 */
    public static String idsLine() {
        return String.join(", ", IDS);
    }

    /** 从模型回复里取 PATTERN 行；不合法（不在枚举里）返回 null。 */
    public static String extract(String text) {
        if (text == null) {
            return null;
        }
        for (String line : text.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.startsWith("PATTERN:")) {
                continue;
            }
            String id = trimmed.substring("PATTERN:".length()).strip().toLowerCase();
            int space = id.indexOf(' ');
            if (space > 0) {
                id = id.substring(0, space);
            }
            return IDS.contains(id) ? id : null;
        }
        return null;
    }

    /** 把 PATTERN 行从展示文本里剥掉（它不该出现在提示正文和历史里）。 */
    public static String strip(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.strip().startsWith("PATTERN:")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString().replaceAll("\\A\\s*\n", "").stripTrailing();
    }

    /** 题目标签兜底：模型没给 PATTERN 时，用题面的「标签」猜一个。 */
    public static String fromProblem(Repo repo, String id) {
        Path markdown = repo.problemMd(id);
        if (!Files.isRegularFile(markdown)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(markdown)) {
                String trimmed = line.strip();
                if (!trimmed.startsWith(">")) {
                    continue;
                }
                int at = trimmed.indexOf("标签：");
                if (at < 0) {
                    continue;
                }
                for (String tag : trimmed.substring(at + 3).split(" / ")) {
                    String mapped = TAG_MAP.get(tag.strip());
                    if (mapped != null && find(repo, mapped).isPresent()) {
                        return mapped;
                    }
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    public static boolean seen(Repo repo, String id) {
        Path log = repo.root().resolve(".dojo").resolve("learned.log");
        if (!Files.isRegularFile(log)) {
            return false;
        }
        try {
            for (String line : Files.readAllLines(log)) {
                if (line.startsWith(id + "\t")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    public static void markSeen(Repo repo, String id) {
        if (seen(repo, id)) {
            return;
        }
        Path log = repo.root().resolve(".dojo").resolve("learned.log");
        try {
            Files.createDirectories(log.getParent());
            Files.writeString(log, id + "\t" + LocalDateTime.now() + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 记录失败不影响提示本身
        }
    }

    /** L4 之后追加的「下一步三选一」；认不出模式、表里没有、或模式是 design 时返回 null。 */
    public static String tail(Repo repo, String id, boolean seen) {
        return tail(repo, id, seen, null);
    }

    /** currentId 用来避免「先练」指向当前这道题自己。 */
    public static String tail(Repo repo, String id, boolean seen, String currentId) {
        Optional<Pattern> found = find(repo, id);
        if (found.isEmpty()) {
            return null;
        }
        Pattern pattern = found.get();
        if (seen) {
            return "上次给过：" + pattern.name() + "（dojo learn " + pattern.id() + "）";
        }
        List<String> before = new ArrayList<>(pattern.before());
        if (currentId != null) {
            String number = Naming.number(currentId).replaceFirst("^0+(?=\\d)", "");
            before.removeIf(item -> item.matches("^" + java.util.regex.Pattern.quote(number) + "\\b.*"));
        }
        StringBuilder out = new StringBuilder();
        out.append("┌ 这一步过不去的话，三选一\n");
        if (!before.isEmpty()) {
            out.append("│ 先练：").append(String.join(" · ", before)).append('\n');
        }
        out.append("│ ").append(pattern.snippet().isEmpty() ? "看思路：" : "看模板：")
                .append("dojo learn ").append(pattern.id());
        if (!pattern.snippet().isEmpty()) {
            out.append("（可复制到 scratch 跑）");
        }
        out.append('\n');
        out.append("│ 直接要答案：dojo hint 5\n");
        out.append('└');
        return out.toString();
    }

    public static String render(Repo repo, Pattern pattern) {
        StringBuilder out = new StringBuilder();
        out.append(pattern.name()).append(" · ").append(pattern.id()).append('\n');
        if (!pattern.essence().isEmpty()) {
            out.append("本质  ").append(pattern.essence()).append('\n');
        }
        if (!pattern.signals().isEmpty()) {
            out.append("信号  · ").append(pattern.signals().get(0)).append('\n');
            for (int i = 1; i < pattern.signals().size(); i++) {
                out.append("      · ").append(pattern.signals().get(i)).append('\n');
            }
        }
        if (!pattern.prereqs().isEmpty()) {
            out.append("前置  ").append(String.join("、", pattern.prereqs())).append('\n');
        }
        if (!pattern.skills().isEmpty()) {
            out.append("子技能 ").append(String.join(" / ", pattern.skills())).append('\n');
        }
        if (!pattern.snippet().isEmpty()) {
            Path snippet = repo.root().resolve(pattern.snippet());
            out.append('\n').append("模板（").append(pattern.snippet()).append("）\n");
            out.append("┌──────────────────────────────────────────────\n");
            if (Files.isRegularFile(snippet)) {
                try {
                    for (String line : Files.readAllLines(snippet)) {
                        out.append("│ ").append(line).append('\n');
                    }
                } catch (IOException e) {
                    out.append("│ （读不了模板文件：").append(e.getMessage()).append("）\n");
                }
            } else {
                out.append("│ （模板文件缺失：").append(pattern.snippet()).append("）\n");
            }
            out.append("└──────────────────────────────────────────────\n");
        }
        if (!pattern.before().isEmpty()) {
            out.append('\n').append("先练  ").append(String.join("\n      ", pattern.before())).append('\n');
        }
        if (!pattern.practice().isEmpty()) {
            out.append("巩固  ").append(String.join(" · ", pattern.practice())).append('\n');
        }
        if (!pattern.link().isEmpty()) {
            out.append('\n').append("延伸  ").append(pattern.link());
            if (!pattern.backup().isEmpty()) {
                out.append("   （备选：").append(pattern.backup()).append("）");
            }
            out.append('\n');
        }
        if (!pattern.pitfalls().isEmpty()) {
            out.append("易错  · ").append(pattern.pitfalls().get(0)).append('\n');
            for (int i = 1; i < pattern.pitfalls().size(); i++) {
                out.append("      · ").append(pattern.pitfalls().get(i)).append('\n');
            }
        }
        return out.toString().stripTrailing();
    }
}
