package dojo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生成「随机对拍」用的 JUnit 源码：同一组随机输入分别喂给 Solution 和 Brute，结果必须一致。
 * 方法签名从磁盘上的 Solution.java 读——不依赖当初拉题时的 metaData。
 */
public final class StressGen {

    public record Param(String name, String type) {
    }

    public record Spec(String method, List<Param> params, String returnType) {
    }

    public static final class Unsupported extends RuntimeException {
        public Unsupported(String message) {
            super(message);
        }
    }

    private static final Set<String> SUPPORTED = Set.of("int", "long", "double", "boolean", "char", "String",
            "int[]", "long[]", "double[]", "boolean[]", "char[]", "String[]", "int[][]", "char[][]",
            "ListNode", "TreeNode");

    private StressGen() {
    }

    /** 从 Solution.java 里读第一个可对拍的方法。 */
    public static Spec parse(String source) {
        String cleaned = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
        Matcher m = Pattern.compile(
                "(?m)^[ \\t]*(?:(?:public|private|protected|static|final|synchronized)\\s+)*"
                        + "([\\w$.<>\\[\\], ]+?)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{")
                .matcher(cleaned);
        while (m.find()) {
            String ret = normalize(m.group(1));
            String name = m.group(2);
            if (!SUPPORTED.contains(ret)) {
                continue;
            }
            if (name.equals(ret) || name.equals("main")) {
                continue;
            }
            List<Param> params = new ArrayList<>();
            for (String part : splitParams(m.group(3))) {
                if (part.isBlank()) {
                    continue;
                }
                String[] bits = part.strip().replaceAll("\\bfinal\\s+", "").trim().split("\\s+");
                if (bits.length < 2) {
                    throw new Unsupported("读不懂参数「" + part.strip() + "」");
                }
                String type = normalize(bits[bits.length - 2]);
                String pname = bits[bits.length - 1];
                if (!SUPPORTED.contains(type)) {
                    throw new Unsupported("随机输入还不支持类型 " + type + "（参数 " + pname + "）");
                }
                params.add(new Param(pname, type));
            }
            return new Spec(name, params, ret);
        }
        throw new Unsupported("没在 Solution.java 里找到可对拍的方法（设计题或签名太特殊）；先手写测试");
    }

    public static String signature(Spec spec) {
        StringBuilder sb = new StringBuilder("public ").append(spec.returnType()).append(' ')
                .append(spec.method()).append('(');
        for (int i = 0; i < spec.params().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(spec.params().get(i).type()).append(' ').append(spec.params().get(i).name());
        }
        return sb.append(')').toString();
    }

    public static String testSource(String pkg, String testClass, Spec spec, int cases, long seedBase) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import java.util.*;\n\n");
        sb.append("import org.junit.jupiter.api.Test;\n\n");
        Set<String> kit = new LinkedHashSet<>();
        for (Param p : spec.params()) {
            if (p.type().equals("ListNode")) {
                kit.add("ListNode");
                kit.add("ListNodes");
            }
            if (p.type().equals("TreeNode")) {
                kit.add("TreeNode");
                kit.add("TreeNodes");
            }
        }
        if (spec.returnType().equals("ListNode")) {
            kit.add("ListNode");
            kit.add("ListNodes");
        }
        if (spec.returnType().equals("TreeNode")) {
            kit.add("TreeNode");
            kit.add("TreeNodes");
        }
        if (!kit.isEmpty()) {
            sb.append("import kit.").append(String.join(";\nimport kit.", kit)).append(";\n\n");
        }
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
        sb.append("/** dojo 随机对拍：同一组输入分别喂给 Solution 与 Brute。失败信息里的 seed 可以复现。 */\n");
        sb.append("class ").append(testClass).append(" {\n\n");
        sb.append("    private final Solution solution = new Solution();\n");
        sb.append("    private final Brute brute = new Brute();\n\n");
        sb.append("    @Test\n");
        sb.append("    void randomDifferential() {\n");
        sb.append("        for (int seed = ").append(seedBase).append("; seed < ")
                .append(seedBase + cases).append("; seed++) {\n");
        sb.append("            Random rnd = new Random(seed * 1000003L);\n");

        Set<String> taken = new LinkedHashSet<>(List.of("rnd", "seed", "expected", "actual", "solution", "brute"));
        List<String> argNames = new ArrayList<>();
        for (Param p : spec.params()) {
            argNames.add(safe(p.name(), taken));
        }
        String arrayName = null;
        for (int i = 0; i < spec.params().size(); i++) {
            if (spec.params().get(i).type().equals("int[]") && arrayName == null) {
                arrayName = argNames.get(i);
            }
        }
        for (int i = 0; i < spec.params().size(); i++) {
            sb.append(lines(spec.params().get(i), argNames.get(i), arrayName));
        }

        if (uniqueTwoSum(spec)) {
            sb.append("            int pairs = 0;\n");
            sb.append("            for (int i = 0; i < ").append(argNames.get(0)).append(".length; i++) {\n");
            sb.append("                for (int j = i + 1; j < ").append(argNames.get(0)).append(".length; j++) {\n");
            sb.append("                    if (").append(argNames.get(0)).append("[i] + ").append(argNames.get(0))
                    .append("[j] == ").append(argNames.get(1)).append(") pairs++;\n");
            sb.append("                }\n");
            sb.append("            }\n");
            sb.append("            if (pairs != 1) continue;  // 题面保证唯一答案；多解输入只测出假警报\n");
        }

        String ret = spec.returnType();
        String call = spec.method() + "(" + String.join(", ", argNames) + ")";
        sb.append("            ").append(ret).append(" expected = brute.").append(call).append(";\n");
        sb.append("            ").append(ret).append(" actual = solution.").append(call).append(";\n");
        sb.append(compare(ret));

        StringBuilder message = new StringBuilder("\"seed=\" + seed");
        for (int i = 0; i < spec.params().size(); i++) {
            Param p = spec.params().get(i);
            message.append(" + \" ").append(p.name()).append("=\" + ").append(show(p.type(), argNames.get(i)));
        }
        sb.append("                throw new AssertionError(").append(message)
                .append(" + \" expected=\" + ").append(show(ret, "expected"))
                .append(" + \" actual=\" + ").append(show(ret, "actual")).append(");\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append(helpers());
        sb.append("}\n");
        return sb.toString();
    }

    /** two-sum 题面保证唯一答案，随机输入不保证——多解会变成假警报。 */
    private static boolean uniqueTwoSum(Spec spec) {
        return spec.method().equals("twoSum") && spec.params().size() == 2
                && spec.params().get(0).type().equals("int[]")
                && spec.params().get(1).type().equals("int")
                && spec.returnType().equals("int[]");
    }

    private static String lines(Param p, String name, String arrayName) {
        String indent = "            ";
        return switch (p.type()) {
            case "int" -> {
                String tail = switch (p.name()) {
                    case "target" -> arrayName != null
                            ? name + " = " + arrayName + ".length >= 2 ? " + arrayName
                                    + "[rnd.nextInt(" + arrayName + ".length)] + " + arrayName
                                    + "[rnd.nextInt(" + arrayName + ".length)] : rnd.nextInt(21) - 10;"
                            : name + " = rnd.nextInt(21) - 10;";
                    case "k", "n", "kth", "index", "count", "size" -> arrayName != null
                            ? name + " = " + arrayName + ".length == 0 ? 0 : 1 + rnd.nextInt(" + arrayName
                                    + ".length);"
                            : name + " = rnd.nextInt(8);";
                    default -> name + " = rnd.nextInt(21) - 10;";
                };
                yield indent + "int " + tail + "\n";
            }
            case "long" -> indent + "long " + name + " = rnd.nextInt(21) - 10;\n";
            case "double" -> indent + "double " + name + " = (rnd.nextInt(21) - 10) / 2.0;\n";
            case "boolean" -> indent + "boolean " + name + " = rnd.nextBoolean();\n";
            case "char" -> indent + "char " + name + " = (char) ('a' + rnd.nextInt(3));\n";
            case "String" -> indent + "String " + name + " = randStr(rnd, 8);\n";
            case "int[]" -> indent + "int[] " + name + " = randInts(rnd, 0, 7);\n";
            case "long[]" -> indent + "long[] " + name + " = randLongs(rnd, 0, 7);\n";
            case "double[]" -> indent + "double[] " + name + " = randDoubles(rnd, 0, 7);\n";
            case "boolean[]" -> indent + "boolean[] " + name + " = randBooleans(rnd, 0, 7);\n";
            case "char[]" -> indent + "char[] " + name + " = randStr(rnd, 8).toCharArray();\n";
            case "String[]" -> indent + "String[] " + name + " = randStrs(rnd, 5);\n";
            case "int[][]" -> indent + "int[][] " + name + " = randIntGrid(rnd, 3);\n";
            case "char[][]" -> indent + "char[][] " + name + " = randGrid(rnd, 4);\n";
            case "ListNode" -> indent + "ListNode " + name + " = ListNodes.of(randInts(rnd, 0, 6));\n";
            case "TreeNode" -> indent + "TreeNode " + name + " = TreeNodes.of(randTree(rnd));\n";
            default -> throw new Unsupported("随机输入不支持 " + p.type());
        };
    }

    private static String compare(String ret) {
        String indent = "            ";
        String check = switch (ret) {
            case "int", "long", "boolean", "char" -> "if (expected != actual) {";
            case "double" -> "if (Math.abs(expected - actual) > 1e-6) {";
            case "String" -> "if (!Objects.equals(expected, actual)) {";
            case "int[]", "long[]", "double[]", "boolean[]", "char[]", "String[]" ->
                    "if (!Arrays.equals(expected, actual)) {";
            case "int[][]", "char[][]" -> "if (!Arrays.deepEquals(expected, actual)) {";
            case "ListNode" -> "if (!Arrays.equals(ListNodes.toArray(expected), ListNodes.toArray(actual))) {";
            case "TreeNode" -> "if (!Arrays.equals(TreeNodes.toArray(expected), TreeNodes.toArray(actual))) {";
            default -> throw new Unsupported("随机对拍不支持返回类型 " + ret);
        };
        return indent + check + "\n";
    }

    private static String show(String type, String expr) {
        return switch (type) {
            case "int[]", "long[]", "double[]", "boolean[]", "char[]", "String[]" -> "Arrays.toString(" + expr + ")";
            case "int[][]", "char[][]" -> "Arrays.deepToString(" + expr + ")";
            case "char" -> "String.valueOf(" + expr + ")";
            case "ListNode" -> "Arrays.toString(ListNodes.toArray(" + expr + "))";
            case "TreeNode" -> "Arrays.toString(TreeNodes.toArray(" + expr + "))";
            case "String" -> "\"\\\"\" + " + expr + " + \"\\\"\"";
            default -> "String.valueOf(" + expr + ")";
        };
    }

    private static String helpers() {
        return """
                    private static int[] randInts(Random rnd, int min, int max) {
                        int n = min + rnd.nextInt(max - min + 1);
                        int[] a = new int[n];
                        for (int i = 0; i < n; i++) {
                            a[i] = rnd.nextInt(21) - 10;
                        }
                        return a;
                    }

                    private static long[] randLongs(Random rnd, int min, int max) {
                        int n = min + rnd.nextInt(max - min + 1);
                        long[] a = new long[n];
                        for (int i = 0; i < n; i++) {
                            a[i] = rnd.nextInt(21) - 10;
                        }
                        return a;
                    }

                    private static double[] randDoubles(Random rnd, int min, int max) {
                        int n = min + rnd.nextInt(max - min + 1);
                        double[] a = new double[n];
                        for (int i = 0; i < n; i++) {
                            a[i] = (rnd.nextInt(21) - 10) / 2.0;
                        }
                        return a;
                    }

                    private static boolean[] randBooleans(Random rnd, int min, int max) {
                        int n = min + rnd.nextInt(max - min + 1);
                        boolean[] a = new boolean[n];
                        for (int i = 0; i < n; i++) {
                            a[i] = rnd.nextBoolean();
                        }
                        return a;
                    }

                    private static String randStr(Random rnd, int max) {
                        int n = rnd.nextInt(max + 1);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < n; i++) {
                            sb.append((char) ('a' + rnd.nextInt(3)));
                        }
                        return sb.toString();
                    }

                    private static String[] randStrs(Random rnd, int max) {
                        int n = rnd.nextInt(max + 1);
                        String[] a = new String[n];
                        for (int i = 0; i < n; i++) {
                            a[i] = randStr(rnd, 4);
                        }
                        return a;
                    }

                    private static char[][] randGrid(Random rnd, int max) {
                        int rows = 1 + rnd.nextInt(max);
                        int cols = 1 + rnd.nextInt(max);
                        char[][] grid = new char[rows][cols];
                        for (int i = 0; i < rows; i++) {
                            for (int j = 0; j < cols; j++) {
                                grid[i][j] = rnd.nextBoolean() ? '1' : '0';
                            }
                        }
                        return grid;
                    }

                    private static int[][] randIntGrid(Random rnd, int max) {
                        int rows = rnd.nextInt(max + 1);
                        int cols = rnd.nextInt(max + 1);
                        int[][] grid = new int[rows][cols];
                        for (int i = 0; i < rows; i++) {
                            for (int j = 0; j < cols; j++) {
                                grid[i][j] = rnd.nextInt(11) - 5;
                            }
                        }
                        return grid;
                    }

                    private static Integer[] randTree(Random rnd) {
                        int n = 1 + rnd.nextInt(7);
                        Integer[] a = new Integer[n];
                        a[0] = rnd.nextInt(15) - 7;
                        for (int i = 1; i < n; i++) {
                            a[i] = rnd.nextInt(7) == 0 ? null : rnd.nextInt(15) - 7;
                        }
                        return a;
                    }
                """;
    }

    private static String normalize(String type) {
        return type.replaceAll("\\s+", "").replace("java.lang.", "");
    }

    private static List<String> splitParams(String text) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '<' || c == '[') {
                depth++;
            } else if (c == '>' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                out.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        out.add(current.toString());
        return out;
    }

    private static String safe(String name, Set<String> taken) {
        String base = name == null || name.isBlank() ? "arg" : name.replaceAll("[^A-Za-z0-9_]", "_");
        if (Character.isDigit(base.charAt(0))) {
            base = "arg" + base;
        }
        if (JAVA_WORDS.contains(base)) {
            base = base + "_";
        }
        String candidate = base;
        int i = 2;
        while (taken.contains(candidate)) {
            candidate = base + i++;
        }
        taken.add(candidate);
        return candidate;
    }

    private static final Set<String> JAVA_WORDS = Set.of("class", "int", "long", "double", "boolean", "char",
            "for", "while", "if", "else", "return", "new", "static", "final", "public", "private", "protected",
            "void", "do", "switch", "case", "break", "continue", "this", "super", "null", "true", "false");
}
