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

    /** 题面自己说顺序无关的（三数之和、子集、组合…），就别用严格顺序去卡用户。 */
    private static final Pattern LOOSE_ORDER = Pattern.compile(
            "(?:顺序|次序)[^。；\\n]{0,6}不(?:重要|作要求)|任意顺序|任何顺序|顺序无关|不要求顺序");

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
        List<Example> examples = parseExamples(problem.contentMd());
        if (examples.isEmpty()) {
            throw new Unsupported("题面里没有找到「输出：」行");
        }

        boolean orderFree = unordered || LOOSE_ORDER.matcher(problem.contentMd()).find();
        List<String> tests = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<Integer> used = new java.util.HashSet<>();
        for (int i = 0; i < cases.size(); i++) {
            try {
                int match = matchExample(cases.get(i), examples, used, i, cases.size() == examples.size());
                if (match < 0) {
                    throw new Unsupported("题面示例（" + examples.size() + " 个）里没有与之对应的输入");
                }
                used.add(match);
                tests.add(methodTest("officialExample" + (i + 1), method, params, type, outputIndex,
                        cases.get(i), examples.get(match).rawOutput(), orderFree));
            } catch (Unsupported e) {
                notes.add("示例 " + (i + 1) + " 跳过：" + e.getMessage());
            }
        }
        if (tests.isEmpty()) {
            throw new Unsupported("官方样例都生成不了：" + String.join("；", notes));
        }
        String source = wrap(pkg, testClass, true, tests, notes);
        String note = "已生成 " + tests.size() + "/" + cases.size() + " 个官方样例"
                + (orderFree && !unordered ? "（题面说顺序无关，按无序比较）" : "")
                + (notes.isEmpty() ? "" : "（" + String.join("；", notes) + "）");
        return new Generated(source, note);
    }

    /** 题面里的一段「输入 … 输出 …」。inputs 解析失败时为 null（只用于按位置兜底）。 */
    record Example(List<Object> inputs, String rawOutput) {
    }

    private static final Pattern INPUT_LABEL = Pattern.compile(
            "^\\s*(?:[-*]\\s*)?(?:\\*\\*|__)?\\s*(?:输入|Input)\\s*(?:\\*\\*|__)?\\s*[：:]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OUTPUT_LABEL = Pattern.compile(
            "^\\s*(?:[-*]\\s*)?(?:\\*\\*|__)?\\s*(?:输出|Output)\\s*(?:\\*\\*|__)?\\s*[：:]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_INPUT = Pattern.compile(
            "^\\s*(?:[-*]\\s*)?(?:\\*\\*|__)?\\s*(?:输入|Input)\\s*(?:\\*\\*|__)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_OUTPUT = Pattern.compile(
            "^\\s*(?:[-*]\\s*)?(?:\\*\\*|__)?\\s*(?:输出|Output)\\s*(?:\\*\\*|__)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 把题面里的示例解析成「输入值 + 输出原文」。
     *
     * <p>不能按下标硬配：leetcode.cn 的 jsonExampleTestcases 有时只保留 3 个用例，
     * 而题面写了 5 个（第 13 题就是），按下标配会把 58 当成 4。
     */
    static List<Example> parseExamples(String markdown) {
        List<Example> insideFence = new ArrayList<>();
        List<Example> everywhere = new ArrayList<>();
        if (markdown == null) {
            return List.of();
        }
        boolean fence = false;
        List<String> lines = List.of(markdown.split("\n"));
        StringBuilder chunk = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.strip().startsWith("```")) {
                fence = !fence;
                continue;
            }
            Matcher input = INPUT_LABEL.matcher(line);
            boolean bareInput = BARE_INPUT.matcher(line).matches();
            if (input.matches() || bareInput) {
                chunk = new StringBuilder(input.matches() ? input.group(1) : "");
                continue;
            }
            Matcher output = OUTPUT_LABEL.matcher(line);
            boolean bareOutput = BARE_OUTPUT.matcher(line).matches();
            if (chunk != null && (output.matches() || bareOutput)) {
                String raw = output.matches() ? output.group(1) : "";
                raw = raw == null ? "" : raw.strip();
                if (raw.isEmpty()) {
                    raw = nextJsonLine(lines.toArray(new String[0]), i + 1);
                }
                if (raw != null && !raw.isBlank()) {
                    Example example = new Example(parseInputValues(chunk.toString()), raw.strip());
                    everywhere.add(example);
                    if (fence) {
                        insideFence.add(example);
                    }
                }
                chunk = null;
                continue;
            }
            if (chunk != null) {
                chunk.append('\n').append(line);
            }
        }
        return insideFence.isEmpty() ? everywhere : insideFence;
    }

    /** 输入块 → 值列表。`nums = [2,7,11,15], target = 9` 这种按顶层逗号切。 */
    static List<Object> parseInputValues(String chunk) {
        String text = chunk.replace('\r', ' ').strip();
        if (text.isEmpty()) {
            return null;
        }
        List<String> parts = splitTopLevel(text);
        List<Object> values = new ArrayList<>();
        for (String part : parts) {
            String segment = part.strip();
            if (segment.isEmpty()) {
                continue;
            }
            int eq = segment.indexOf('=');
            String value = eq >= 0 ? segment.substring(eq + 1).strip() : segment;
            try {
                values.add(Json.parse(value));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return values.isEmpty() ? null : values;
    }

    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                current.append(c);
                if (c == '\\') {
                    if (i + 1 < text.length()) {
                        current.append(text.charAt(++i));
                    }
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                current.append(c);
            } else if (c == '[' || c == '{' || c == '(') {
                depth++;
                current.append(c);
            } else if (c == ']' || c == '}' || c == ')') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static int matchExample(String rawCase, List<Example> examples, Set<Integer> used,
                                    int position, boolean sameCount) {
        List<Object> values;
        try {
            List<Object> parsed = new ArrayList<>();
            for (String line : splitLines(rawCase)) {
                parsed.add(parseValue(line));
            }
            values = parsed;
        } catch (Unsupported e) {
            return -1;
        }
        for (int j = 0; j < examples.size(); j++) {
            if (used.contains(j) || examples.get(j).inputs() == null) {
                continue;
            }
            if (sameValues(examples.get(j).inputs(), values)) {
                return j;
            }
        }
        // 示例数与用例数一致时退回按位置配：题面示例的输入写法可能有非 JSON 形式（很少见）。
        if (sameCount && !used.contains(position)) {
            return position;
        }
        return -1;
    }

    private static boolean sameValues(List<Object> a, List<Object> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!java.util.Objects.equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
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
        if (rawExpected == null) {
            throw new Unsupported("缺少对应的期望输出");
        }
        Object expectedValue = parseValue(rawExpected);

        StringBuilder sb = new StringBuilder();
        sb.append("    @Test\n    void ").append(label).append("() {\n");
        List<String> names = new ArrayList<>();
        // 解法对象叫 solution；参数名不能把它遮住（比如题目参数就叫 solution）
        List<String> taken = new ArrayList<>(List.of("solution", "actual"));
        for (int i = 0; i < params.size(); i++) {
            Param param = params.get(i);
            String name = safeName(param.name(), "arg" + i, taken);
            names.add(name);
            taken.add(name);
            sb.append("        ").append(javaType(param.type())).append(' ').append(name)
                    .append(" = ").append(literal(param.type(), values.get(i))).append(";\n");
        }
        if ("void".equals(type)) {
            if (outputIndex < 0 || outputIndex >= params.size()) {
                throw new Unsupported("void 题缺少合法的 output.paramindex");
            }
            sb.append("        solution.").append(method).append('(').append(String.join(", ", names)).append(");\n");
            sb.append(assertion(params.get(outputIndex).type(), expectedValue, names.get(outputIndex), unordered));
        } else {
            String actual = "actual";
            while (names.contains(actual)) {
                actual = actual + "_";
            }
            sb.append("        ").append(javaType(type)).append(' ').append(actual)
                    .append(" = solution.").append(method).append('(').append(String.join(", ", names)).append(");\n");
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
            case "list<string>", "list<integer>" -> {
                return "        assertEquals(" + literal(t, expected) + ", " + actual + ");\n";
            }
            case "list<list<integer>>" -> {
                if (unordered) {
                    return "        assertEquals(normInts(" + literal(t, expected) + "), normInts(" + actual + "));\n";
                }
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
        boolean usesNormInts = body.contains("normInts(");

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
            sb.append("    private final Solution solution = new Solution();\n\n");
        }
        sb.append(body);
        if (!notes.isEmpty()) {
            sb.append('\n');
            for (String note : notes) {
                sb.append("    // dojo: ").append(note).append('\n');
            }
        }
        if (usesNormInts) {
            sb.append("""

                        private static List<List<Integer>> normInts(List<List<Integer>> value) {
                            List<List<Integer>> out = new ArrayList<>();
                            for (List<Integer> inner : value) {
                                List<Integer> copy = new ArrayList<>(inner);
                                Collections.sort(copy);
                                out.add(copy);
                            }
                            out.sort(Comparator.comparing(Object::toString));
                            return out;
                        }
                    """);
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
