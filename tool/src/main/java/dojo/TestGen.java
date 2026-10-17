package dojo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用题目的 metaData + 官方样例，确定性地生成 JUnit 测试。
 *
 * <p>这是「不阻塞」的关键：官方样例能直接变成可运行的断言，用户拿到题就能
 * 先跑一次看到红/绿，而不是先手抄一堆 [[1,2],[3]]。生成不了的题（类型太怪、
 * metaData 不可信）宁可明确跳过并说明原因，也不留一个编译不过的测试文件。
 * 这里不调模型——测试必须可复现。
 */
public final class TestGen {

    public record Generated(String source, String note) {
    }

    private record Param(String name, String type) {
    }

    private record Method(String name, List<Param> params, String returnType) {
    }

    private static final class Unsupported extends RuntimeException {
        Unsupported(String message) {
            super(message);
        }
    }

    private static final Pattern OUTPUT_LINE = Pattern.compile(
            "^\\s*(?:[-*]\\s*)?(?:\\*\\*|__)?\\s*(?:输出|Output)\\s*(?:\\*\\*|__)?\\s*[：:]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null", "var", "record", "yield");

    private TestGen() {
    }

    public static Generated generate(Problem problem, String pkg, String testClass, boolean unordered) {
        if (problem.metaDataJson() == null || problem.metaDataJson().isBlank()) {
            return new Generated(null, "题目没有 metaData，官方样例无法自动生成");
        }
        Object meta;
        try {
            meta = Json.parse(problem.metaDataJson());
        } catch (IllegalArgumentException e) {
            return new Generated(null, "metaData 不是合法 JSON：" + e.getMessage());
        }
        try {
            if (Json.strAt(meta, "classname") != null && Json.at(meta, "methods") != null) {
                return design(problem, meta, pkg, testClass);
            }
            return method(problem, meta, pkg, testClass, unordered);
        } catch (Unsupported e) {
            return new Generated(null, e.getMessage());
        } catch (RuntimeException e) {
            return new Generated(null, "解析 metaData 失败：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 普通方法题

    private static Generated method(Problem problem, Object meta, String pkg, String testClass,
                                    boolean unordered) {
        String method = Json.strAt(meta, "name");
        if (method == null || method.isBlank()) {
            throw new Unsupported("metaData 里没有方法名");
        }
        List<Param> params = params(Json.at(meta, "params"));
        String returnType = Json.strAt(meta, "return", "type");
        if (returnType == null) {
            throw new Unsupported("metaData 里没有返回类型");
        }
        int outputIndex = intValue(Json.at(meta, "output", "paramindex"), -1);
        String type = normalize(returnType);

        List<String> cases = exampleCases(problem);
        if (cases.isEmpty()) {
            throw new Unsupported("题目没有 jsonExampleTestcases");
        }
        List<String> expected = expectedOutputs(problem.contentMd());
        if (expected.isEmpty()) {
            throw new Unsupported("题面里没有找到「输出：」行");
        }

        List<String> tests = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            String rawExpected = i < expected.size() ? expected.get(i) : null;
            try {
                tests.add(methodTest("officialExample" + (i + 1), method, params, type, outputIndex,
                        cases.get(i), rawExpected, unordered));
            } catch (Unsupported e) {
                notes.add("示例 " + (i + 1) + " 跳过：" + e.getMessage());
            }
        }
        if (tests.isEmpty()) {
            throw new Unsupported("官方样例都生成不了：" + String.join("；", notes));
        }
        String source = wrap(pkg, testClass, true, tests, notes);
        String note = "已生成 " + tests.size() + "/" + cases.size() + " 个官方样例"
                + (notes.isEmpty() ? "" : "（" + String.join("；", notes) + "）");
        return new Generated(source, note);
    }

    private static String methodTest(String label, String method, List<Param> params, String type,
                                     int outputIndex, String rawCase, String rawExpected, boolean unordered) {
        String[] lines = splitLines(rawCase);
        if (lines.length != params.size()) {
            throw new Unsupported("输入行数 " + lines.length + " ≠ 参数个数 " + params.size());
        }
        List<Object> values = new ArrayList<>();
        for (String line : lines) {
            values.add(parseValue(line));
        }
        boolean hasExpected = rawExpected != null;
        if (!hasExpected) {
            throw new Unsupported("缺少对应的期望输出");
        }
        Object expectedValue = parseValue(rawExpected);

        StringBuilder sb = new StringBuilder();
        sb.append("    @Test\n    void ").append(label).append("() {\n");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            Param param = params.get(i);
            String name = safeName(param.name(), "arg" + i, names);
            names.add(name);
            sb.append("        ").append(javaType(param.type())).append(' ').append(name)
                    .append(" = ").append(literal(param.type(), values.get(i))).append(";\n");
        }
        if ("void".equals(type)) {
            if (outputIndex < 0 || outputIndex >= params.size()) {
                throw new Unsupported("void 题缺少合法的 output.paramindex");
            }
            sb.append("        s.").append(method).append('(').append(String.join(", ", names)).append(");\n");
            sb.append(assertion(params.get(outputIndex).type(), expectedValue, names.get(outputIndex), unordered));
        } else {
            String actual = "actual";
            while (names.contains(actual)) {
                actual = actual + "_";
            }
            sb.append("        ").append(javaType(type)).append(' ').append(actual)
                    .append(" = s.").append(method).append('(').append(String.join(", ", names)).append(");\n");
            sb.append(assertion(type, expectedValue, actual, unordered));
        }
        sb.append("    }\n");
        return sb.toString();
    }

    // ---------------------------------------------------------------- 设计题

    private static Generated design(Problem problem, Object meta, String pkg, String testClass) {
        String className = Json.strAt(meta, "classname");
        List<String> cases = exampleCases(problem);
        if (cases.isEmpty()) {
            throw new Unsupported("设计题没有 jsonExampleTestcases");
        }
        String[] lines = splitLines(cases.get(0));
        if (lines.length < 2) {
            throw new Unsupported("设计题样例格式不认识（应为「操作名数组 + 参数数组」两行）");
        }
        List<String> ops = Json.arrOfStrings(Json.parse(lines[0]));
        List<Object> argRows = Json.arr(Json.parse(lines[1]));
        if (ops.size() != argRows.size()) {
            throw new Unsupported("操作名 " + ops.size() + " 个，参数却 " + argRows.size() + " 组");
        }
        List<String> expected = expectedOutputs(problem.contentMd());
        if (expected.isEmpty()) {
            throw new Unsupported("题面里没有找到「输出：」行");
        }
        Object expectedNode = Json.parse(expected.get(0));
        if (!(expectedNode instanceof List<?> expectedList)) {
            throw new Unsupported("设计题的期望输出不是数组");
        }
        if (expectedList.size() != ops.size()) {
            throw new Unsupported("期望输出 " + expectedList.size() + " 项，操作 " + ops.size() + " 个");
        }

        List<Param> ctorParams = params(Json.at(meta, "constructor", "params"));
        Map<String, Method> methods = methods(Json.at(meta, "methods"));

        List<String> ctorArgs = new ArrayList<>();
        List<Object> ctorValues = Json.arr(argRows.get(0));
        if (ctorValues.size() != ctorParams.size()) {
            throw new Unsupported("构造函数参数个数对不上");
        }
        for (int i = 0; i < ctorParams.size(); i++) {
            ctorArgs.add(literal(ctorParams.get(i).type(), ctorValues.get(i)));
        }

        StringBuilder body = new StringBuilder();
        body.append("    @Test\n    void officialExample1() {\n");
        body.append("        ").append(className).append(" obj = new ").append(className)
                .append('(').append(String.join(", ", ctorArgs)).append(");\n");
        for (int i = 1; i < ops.size(); i++) {
            String op = ops.get(i);
            Method signature = methods.get(op);
            if (signature == null) {
                throw new Unsupported("metaData 里没有方法 " + op);
            }
            List<Object> args = Json.arr(argRows.get(i));
            if (args.size() != signature.params().size()) {
                throw new Unsupported("操作 " + op + " 的参数个数对不上");
            }
            List<String> argLits = new ArrayList<>();
            for (int j = 0; j < args.size(); j++) {
                argLits.add(literal(signature.params().get(j).type(), args.get(j)));
            }
            Object expectedValue = expectedList.get(i);
            String call = "obj." + op + '(' + String.join(", ", argLits) + ");";
            if ("void".equals(normalize(signature.returnType()))) {
                body.append("        ").append(call).append('\n');
            } else {
                body.append("        ").append(nullSafeAssert(signature.returnType(), expectedValue, "obj." + op
                        + '(' + String.join(", ", argLits) + ')')).append('\n');
            }
        }
        body.append("    }\n");

        String source = wrap(pkg, testClass, false, List.of(body.toString()), List.of());
        return new Generated(source, "已按操作序列生成 " + (ops.size() - 1) + " 步调用的官方样例");
    }

    private static String nullSafeAssert(String type, Object expected, String call) {
        if (expected == null) {
            return switch (normalize(type)) {
                case "string", "list<string>", "list<integer>", "list<list<string>>", "list<list<integer>>",
                        "listnode", "treenode" -> "assertNull(" + call + ");";
                default -> throw new Unsupported("期望输出是 null，但返回类型是 " + type);
            };
        }
        return "assertEquals(" + literal(type, expected) + ", " + call + ");";
    }

    // ---------------------------------------------------------------- 断言

    private static String assertion(String type, Object expected, String actual, boolean unordered) {
        String t = normalize(type);
        if (expected == null) {
            return switch (t) {
                case "string", "list<string>", "list<integer>", "list<list<string>>", "list<list<integer>>",
                        "listnode", "treenode" -> "        assertNull(" + actual + ");\n";
                default -> throw new Unsupported("期望输出为空，无法与 " + type + " 比较");
            };
        }
        switch (t) {
            case "integer", "long", "boolean", "string", "character" -> {
                return "        assertEquals(" + literal(t, expected) + ", " + actual + ");\n";
            }
            case "double" -> {
                return "        assertEquals(" + literal(t, expected) + ", " + actual + ", 1e-5);\n";
            }
            case "integer[]", "string[]", "character[]" -> {
                if (unordered) {
                    String javaType = javaType(t);
                    return "        " + javaType + " expected = " + literal(t, expected) + ";\n"
                            + "        " + javaType + " got = " + actual + ".clone();\n"
                            + "        Arrays.sort(expected);\n"
                            + "        Arrays.sort(got);\n"
                            + "        assertArrayEquals(expected, got);\n";
                }
                return "        assertArrayEquals(" + literal(t, expected) + ", " + actual + ");\n";
            }
            case "integer[][]", "string[][]", "character[][]" -> {
                return "        assertTrue(Arrays.deepEquals(" + literal(t, expected) + ", " + actual + "));\n";
            }
            case "list<string>", "list<integer>", "list<list<integer>>" -> {
                return "        assertEquals(" + literal(t, expected) + ", " + actual + ");\n";
            }
            case "list<list<string>>" -> {
                return "        assertEquals(norm(" + literal(t, expected) + "), norm(" + actual + "));\n";
            }
            case "listnode" -> {
                return "        assertArrayEquals(" + literal("integer[]", expected) + ", ListNodes.toArray("
                        + actual + "));\n";
            }
            case "treenode" -> {
                return "        assertArrayEquals(" + integerArray(list(expected), true) + ", TreeNodes.toArray("
                        + actual + "));\n";
            }
            default -> throw new Unsupported("还不支持返回类型 " + type);
        }
    }

    // ---------------------------------------------------------------- 字面量

    private static String literal(String type, Object value) {
        String t = normalize(type);
        if (value == null) {
            return switch (t) {
                case "string", "list<string>", "list<integer>", "list<list<string>>", "list<list<integer>>",
                        "listnode", "treenode", "integer[]", "string[]", "character[]", "integer[][]",
                        "string[][]", "character[][]" -> "null";
                default -> throw new Unsupported("原始类型 " + type + " 不能是 null");
            };
        }
        switch (t) {
            case "integer" -> {
                return number(value).intValue() + "";
            }
            case "long" -> {
                return number(value).longValue() + "L";
            }
            case "double" -> {
                double d = number(value).doubleValue();
                return Double.toString(d);
            }
            case "boolean" -> {
                return Boolean.TRUE.equals(value) ? "true" : "false";
            }
            case "string" -> {
                return Json.quote(value.toString());
            }
            case "character" -> {
                String s = value.toString();
                if (s.length() != 1) {
                    throw new Unsupported("期望单个字符，实际是 " + s);
                }
                return "'" + escapeChar(s.charAt(0)) + "'";
            }
            case "integer[]" -> {
                return integerArray(list(value), false);
            }
            case "character[]" -> {
                return "new char[]{" + joinLiterals("character", list(value)) + "}";
            }
            case "string[]" -> {
                return "new String[]{" + joinLiterals("string", list(value)) + "}";
            }
            case "integer[][]" -> {
                return "new int[][]{" + joinLiterals("integer[]", list(value)) + "}";
            }
            case "character[][]" -> {
                return "new char[][]{" + joinLiterals("character[]", list(value)) + "}";
            }
            case "string[][]" -> {
                return "new String[][]{" + joinLiterals("string[]", list(value)) + "}";
            }
            case "list<integer>" -> {
                return "List.<Integer>of(" + joinLiterals("integer", list(value)) + ")";
            }
            case "list<string>" -> {
                return "List.<String>of(" + joinLiterals("string", list(value)) + ")";
            }
            case "list<list<string>>" -> {
                return "List.<List<String>>of(" + joinLiterals("list<string>", list(value)) + ")";
            }
            case "list<list<integer>>" -> {
                return "List.<List<Integer>>of(" + joinLiterals("list<integer>", list(value)) + ")";
            }
            case "listnode" -> {
                return "ListNodes.of(" + literal("integer[]", value) + ")";
            }
            case "treenode" -> {
                return "TreeNodes.of(" + integerArray(list(value), true) + ")";
            }
            default -> throw new Unsupported("还不支持类型 " + type);
        }
    }

    /** 允许 null 元素（TreeNode 的层序输入需要），装箱与否决定 int[] 还是 Integer[]。 */
    private static String integerArray(List<Object> values, boolean boxed) {
        List<String> items = new ArrayList<>();
        for (Object value : values) {
            items.add(value == null ? "null" : String.valueOf(number(value).intValue()));
        }
        return (boxed ? "new Integer[]{" : "new int[]{") + String.join(", ", items) + "}";
    }

    private static String joinLiterals(String type, List<Object> values) {
        List<String> out = new ArrayList<>();
        for (Object value : values) {
            out.add(literal(type, value));
        }
        return String.join(", ", out);
    }

    private static String escapeChar(char c) {
        return switch (c) {
            case '\'' -> "\\'";
            case '\\' -> "\\\\";
            case '\n' -> "\\n";
            case '\t' -> "\\t";
            case '\r' -> "\\r";
            default -> String.valueOf(c);
        };
    }

    private static Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        throw new Unsupported("期望数字，实际是 " + value);
    }

    private static List<Object> list(Object value) {
        if (value instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> typed = (List<Object>) list;
            return typed;
        }
        throw new Unsupported("期望数组，实际是 " + value);
    }

    // ---------------------------------------------------------------- 文件骨架

    private static String wrap(String pkg, String testClass, boolean withSolution, List<String> tests,
                               List<String> notes) {
        String body = String.join("\n", tests);
        boolean usesList = body.contains("ListNodes");
        boolean usesTree = body.contains("TreeNodes");
        boolean usesNorm = body.contains("norm(");

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import java.util.*;\n\n");
        sb.append("import org.junit.jupiter.api.Test;\n\n");
        if (usesList) {
            sb.append("import kit.ListNode;\n");
            sb.append("import kit.ListNodes;\n");
        }
        if (usesTree) {
            sb.append("import kit.TreeNode;\n");
            sb.append("import kit.TreeNodes;\n");
        }
        if (usesList || usesTree) {
            sb.append('\n');
        }
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
        sb.append("// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。\n");
        sb.append("class ").append(testClass).append(" {\n\n");
        if (withSolution) {
            sb.append("    private final Solution s = new Solution();\n\n");
        }
        sb.append(body);
        if (!notes.isEmpty()) {
            sb.append('\n');
            for (String note : notes) {
                sb.append("    // dojo: ").append(note).append('\n');
            }
        }
        if (usesNorm) {
            sb.append("""

                        private static List<List<String>> norm(List<List<String>> value) {
                            List<List<String>> out = new ArrayList<>();
                            for (List<String> inner : value) {
                                List<String> copy = new ArrayList<>(inner);
                                Collections.sort(copy);
                                out.add(copy);
                            }
                            out.sort(Comparator.comparing(Object::toString));
                            return out;
                        }
                    """);
        }
        sb.append("}\n");
        return sb.toString();
    }

    // ---------------------------------------------------------------- 解析辅助

    private static List<Param> params(Object node) {
        List<Param> out = new ArrayList<>();
        if (!(node instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            String name = Json.strAt(item, "name");
            String type = Json.strAt(item, "type");
            if (type == null) {
                throw new Unsupported("参数缺少 type");
            }
            out.add(new Param(name == null ? "arg" + out.size() : name, type));
        }
        return out;
    }

    private static Map<String, Method> methods(Object node) {
        Map<String, Method> out = new LinkedHashMap<>();
        if (!(node instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            String name = Json.strAt(item, "name");
            String returnType = Json.strAt(item, "return", "type");
            if (name == null || returnType == null) {
                throw new Unsupported("方法缺少 name 或 return.type");
            }
            out.put(name, new Method(name, params(Json.at(item, "params")), returnType));
        }
        return out;
    }

    private static List<String> exampleCases(Problem problem) {
        if (problem.examplesJson() == null || problem.examplesJson().isBlank()) {
            return List.of();
        }
        try {
            return Json.arrOfStrings(Json.parse(problem.examplesJson()));
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    static List<String> expectedOutputs(String markdown) {
        if (markdown == null) {
            return List.of();
        }
        List<String> insideFence = new ArrayList<>();
        List<String> everywhere = new ArrayList<>();
        boolean fence = false;
        String[] lines = markdown.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.strip().startsWith("```")) {
                fence = !fence;
                continue;
            }
            String value = outputValue(line);
            if (value == null && isBareOutputLabel(line)) {
                // 设计题/新题面是「输入」「输出」各占一行，值在下一行，没有冒号。
                value = nextJsonLine(lines, i + 1);
            }
            if (value != null) {
                everywhere.add(value);
                if (fence) {
                    insideFence.add(value);
                }
            }
        }
        // 示例都在 <pre> 里 → 代码块里；只有找不到时才退化为全文扫描。
        return insideFence.isEmpty() ? everywhere : insideFence;
    }

    private static boolean isBareOutputLabel(String line) {
        String text = line.strip()
                .replaceAll("^(?:[-*]\\s+)", "")
                .replaceAll("^(?:\\*\\*|__)", "")
                .replaceAll("(?:\\*\\*|__)$", "")
                .strip();
        return text.equals("输出") || text.equalsIgnoreCase("output");
    }

    private static String nextJsonLine(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            String candidate = lines[i].strip().replaceAll("^[*`\\s]+", "").replaceAll("[*`\\s]+$", "");
            if (candidate.isEmpty()) {
                continue;
            }
            char first = candidate.charAt(0);
            boolean jsonish = first == '[' || first == '{' || first == '"' || first == '-'
                    || Character.isDigit(first) || candidate.equals("true") || candidate.equals("false")
                    || candidate.equals("null");
            return jsonish ? candidate : null;
        }
        return null;
    }

    private static String outputValue(String line) {
        Matcher matcher = OUTPUT_LINE.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        String value = matcher.group(1)
                .replaceAll("^[*`\\s]+", "")
                .replaceAll("[*`\\s]+$", "");
        return value.isEmpty() ? null : value;
    }

    private static String[] splitLines(String raw) {
        List<String> lines = new ArrayList<>();
        for (String line : raw.replace("\r\n", "\n").strip().split("\n")) {
            lines.add(line.strip());
        }
        return lines.toArray(new String[0]);
    }

    private static Object parseValue(String raw) {
        try {
            return Json.parse(raw.strip());
        } catch (IllegalArgumentException e) {
            throw new Unsupported("值不是合法 JSON：" + raw.strip() + "（" + e.getMessage() + "）");
        }
    }

    private static String safeName(String raw, String fallback, List<String> used) {
        String name = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9_]", "_");
        boolean bad = name.isEmpty()
                || !Character.isJavaIdentifierStart(name.charAt(0))
                || KEYWORDS.contains(name)
                || used.contains(name);
        if (bad) {
            name = fallback;
        }
        while (used.contains(name)) {
            name = name + "_";
        }
        return name;
    }

    private static int intValue(Object node, int fallback) {
        return node instanceof Number number ? number.intValue() : fallback;
    }

    private static String normalize(String type) {
        return type == null ? "" : type.replaceAll("\\s+", "").toLowerCase();
    }

    private static String javaType(String type) {
        return switch (normalize(type)) {
            case "integer" -> "int";
            case "long" -> "long";
            case "double" -> "double";
            case "boolean" -> "boolean";
            case "character" -> "char";
            case "string" -> "String";
            case "integer[]" -> "int[]";
            case "character[]" -> "char[]";
            case "string[]" -> "String[]";
            case "integer[][]" -> "int[][]";
            case "character[][]" -> "char[][]";
            case "string[][]" -> "String[][]";
            case "list<integer>" -> "List<Integer>";
            case "list<string>" -> "List<String>";
            case "list<list<string>>" -> "List<List<String>>";
            case "list<list<integer>>" -> "List<List<Integer>>";
            case "listnode" -> "ListNode";
            case "treenode" -> "TreeNode";
            default -> throw new Unsupported("还不支持类型 " + type);
        };
    }
}
