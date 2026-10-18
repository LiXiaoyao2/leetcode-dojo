package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * dojo fade：把 L5 的完整解法挖掉 3–5 个结构行，变成一道「补完就绿」的练习。
 *
 * <p>依据是 faded worked examples 的结论——把范例逐步撤掉、让学习者补全，比看完整范例有效。
 * 挖空只动结构行（return / 循环条件 / if 条件 / 数组更新），不动机器生成的样板。
 */
public final class Fade {

    private static final Pattern RETURN = Pattern.compile("^\\s*return\\s+[^;]+;.*$");
    private static final Pattern UPDATE = Pattern.compile("^\\s*[\\w.]+(\\[[^\\]]*\\])+\\s*=\\s*.+;.*$");
    private static final Pattern CALL = Pattern.compile("^\\s*[\\w.]+\\(.*\\);\\s*(//.*)?$");

    private Fade() {
    }

    public static int run(Repo repo, String[] args) {
        int lines = 4;
        boolean fromSolution = false;
        boolean force = false;
        boolean restore = false;
        boolean show = false;
        String target = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--lines" -> {
                    if (i + 1 >= args.length) {
                        throw new DojoException("--lines 后面要跟数字，比如 --lines 4。");
                    }
                    lines = Integer.parseInt(args[++i]);
                }
                case "--from" -> {
                    if (i + 1 >= args.length) {
                        throw new DojoException("--from 后面要跟 solution。");
                    }
                    String value = args[++i];
                    if (!value.equals("solution")) {
                        throw new DojoException("--from 只支持 solution（从当前 Solution.java 取代码）。");
                    }
                    fromSolution = true;
                }
                case "--force" -> force = true;
                case "--restore" -> restore = true;
                case "--show" -> show = true;
                case "-h", "--help" -> {
                    usage();
                    return 0;
                }
                default -> {
                    if (arg.startsWith("-")) {
                        throw new DojoException("fade 不认识的选项：" + arg);
                    }
                    target = arg;
                }
            }
        }
        if (lines < 3 || lines > 5) {
            throw new DojoException("--lines 只能是 3–5（默认 4）。");
        }
        String id = repo.resolve(target);
        Path solution = repo.solutionFile(id);
        Path backup = repo.problemDir(id).resolve("solution-full.java");

        if (restore) {
            return restore(repo, id, solution, backup);
        }
        if (show) {
            if (!Files.isRegularFile(backup)) {
                throw new DojoException("没有备份可看：先 `dojo fade` 挖一次。");
            }
            System.out.println(read(backup));
            return 0;
        }

        String source = fromSolution ? readSolution(solution) : lastL5(repo, id);
        if (source == null) {
            source = readSolution(solution);
            if (source == null) {
                throw new DojoException("没有 L5 可挖，也没有 Solution.java。先 `dojo hint 5`，或 `dojo new "
                        + Naming.slug(id) + "`。");
            }
            Ui.info("没有找到 L5，改用当前 Solution.java（要求它现在测试是绿的）。");
        }
        if (Files.isRegularFile(backup) && !force) {
            throw new DojoException("已经有备份 " + repo.root().relativize(backup)
                    + "；先 `dojo fade --restore`，或加 --force 覆盖备份。");
        }
        List<Integer> picked = pickLines(source, lines);
        if (picked.size() < 2) {
            throw new DojoException("这段代码能挖的骨架不够（只找到 " + picked.size()
                    + " 处结构行）。换一道题，或等拿到更完整的 L5。");
        }
        String faded = blank(source, picked);
        try {
            Files.createDirectories(backup.getParent());
            Files.writeString(backup, source, StandardCharsets.UTF_8);
            Files.createDirectories(solution.getParent());
            Files.writeString(solution, faded, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DojoException("写文件失败：" + e.getMessage());
        }

        Ui.ok("已挖掉 " + picked.size() + " 行 → " + repo.root().relativize(solution));
        Ui.info("补全这几行，然后 `dojo test`；全绿就说明这一版你真的会了。");
        Ui.info("对照原文：dojo fade --show    放弃：dojo fade --restore");
        return 0;
    }

    private static void usage() {
        Ui.title("dojo fade");
        System.out.println("  dojo fade                  从最近一次 L5 取代码，挖掉 3–5 行");
        System.out.println("  dojo fade --lines 5        指定挖几行（3–5）");
        System.out.println("  dojo fade --from solution  用当前 Solution.java，而不是 L5");
        System.out.println("  dojo fade --show           打印挖空前的原文");
        System.out.println("  dojo fade --restore        还原挖空前的文件");
    }

    private static int restore(Repo repo, String id, Path solution, Path backup) {
        if (!Files.isRegularFile(backup)) {
            throw new DojoException("没有备份可还原（" + repo.root().relativize(backup) + "）。");
        }
        try {
            Files.createDirectories(solution.getParent());
            Files.writeString(solution, read(backup), StandardCharsets.UTF_8);
            Files.delete(backup);
        } catch (IOException e) {
            throw new DojoException("还原失败：" + e.getMessage());
        }
        Ui.ok("已还原 " + repo.root().relativize(solution) + "，备份已删除。");
        return 0;
    }

    private static String readSolution(Path solution) {
        if (!Files.isRegularFile(solution)) {
            return null;
        }
        String text = read(solution);
        return text.isBlank() ? null : text;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DojoException("读不了 " + path + "：" + e.getMessage());
        }
    }

    /** 从 hints.md 里取最后一个 L5 小节中的 java 代码块。 */
    static String lastL5(Repo repo, String id) {
        Path hints = repo.hintsMd(id);
        if (!Files.isRegularFile(hints)) {
            return null;
        }
        String text = read(hints);
        int start = -1;
        int from = 0;
        while (true) {
            int at = text.indexOf("## L5", from);
            if (at < 0) {
                break;
            }
            start = at;
            from = at + 5;
        }
        if (start < 0) {
            return null;
        }
        String section = text.substring(start);
        int fence = section.indexOf("```java");
        if (fence < 0) {
            return null;
        }
        int begin = section.indexOf('\n', fence);
        int end = section.indexOf("```", begin + 1);
        if (begin < 0 || end < 0) {
            return null;
        }
        String code = section.substring(begin + 1, end);
        return code.isBlank() ? null : code;
    }

    /** 挑 3–5 个逻辑行（return / 数组或字段更新 / 方法调用）：每类最多两行，按源码顺序取。 */
    static List<Integer> pickLines(String source, int want) {
        String[] lines = source.split("\n", -1);
        List<Integer> picked = new ArrayList<>();
        Map<String, Integer> perCategory = new HashMap<>();
        for (int i = 0; i < lines.length && picked.size() < want; i++) {
            String line = lines[i];
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*")
                    || trimmed.startsWith("/*") || trimmed.startsWith("import") || trimmed.startsWith("package")
                    || trimmed.startsWith("@")) {
                continue;
            }
            String category = null;
            if (RETURN.matcher(line).matches()) {
                category = "return";
            } else if (UPDATE.matcher(line).matches()) {
                category = "update";
            } else if (CALL.matcher(line).matches()) {
                category = "call";
            }
            if (category == null || perCategory.getOrDefault(category, 0) >= 2) {
                continue;
            }
            perCategory.merge(category, 1, Integer::sum);
            picked.add(i);
        }
        return picked;
    }

    static String blank(String source, List<Integer> picked) {
        String[] lines = source.split("\n", -1);
        Set<Integer> targets = new LinkedHashSet<>(picked);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (targets.contains(i)) {
                String indent = lines[i].substring(0, lines[i].length() - lines[i].stripLeading().length());
                out.append(indent).append("// TODO: 补回这一行（原第 ").append(i + 1).append(" 行）");
            } else {
                out.append(lines[i]);
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }
}
