package dojo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 够用就好的 JSON：解析网关响应和 LeetCode GraphQL，顺便负责拼请求体。
 *
 * <p>不引第三方库是刻意的：这个工具的全部分价值在「读代码、给提示」，
 * 多一个 jar 就多一个装不上/版本冲突的理由。
 */
public final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text == null ? "" : text;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWs();
        Object value = p.value();
        p.skipWs();
        if (p.pos != p.text.length()) {
            throw new IllegalArgumentException("JSON 尾部有多余内容（位置 " + p.pos + "）");
        }
        return value;
    }

    public static Map<String, Object> obj(Object node) {
        if (node instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        throw new IllegalArgumentException("期望对象，实际是 " + typeName(node));
    }

    public static List<Object> arr(Object node) {
        if (node instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> typed = (List<Object>) list;
            return typed;
        }
        throw new IllegalArgumentException("期望数组，实际是 " + typeName(node));
    }

    public static List<String> arrOfStrings(Object node) {
        List<String> out = new ArrayList<>();
        for (Object item : arr(node)) {
            if (!(item instanceof String s)) {
                throw new IllegalArgumentException("期望字符串数组，遇到 " + typeName(item));
            }
            out.add(s);
        }
        return out;
    }

    public static String str(Object node) {
        return node instanceof String s ? s : null;
    }

    public static Object at(Object node, String... path) {
        Object current = node;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    public static String strAt(Object node, String... path) {
        return str(at(node, path));
    }

    /** 拼请求体用：输出一个合法的 JSON 字符串字面量。 */
    public static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private Object value() {
        char c = peek();
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWs();
            String key = string();
            skipWs();
            expect(':');
            skipWs();
            map.put(key, value());
            skipWs();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new IllegalArgumentException("对象里缺少逗号（位置 " + pos + "）");
            }
        }
    }

    private List<Object> array() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWs();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWs();
            list.add(value());
            skipWs();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new IllegalArgumentException("数组里缺少逗号（位置 " + pos + "）");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char esc = next();
            switch (esc) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw new IllegalArgumentException("未知转义 \\" + esc + "（位置 " + pos + "）");
            }
        }
    }

    private Object number() {
        int start = pos;
        while (pos < text.length() && "+-.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String raw = text.substring(start, pos);
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("无法解析 JSON（位置 " + pos + "）：" + excerpt());
        }
        if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
            return Double.parseDouble(raw);
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return Double.parseDouble(raw);
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, pos)) {
            throw new IllegalArgumentException("期望 " + word + "（位置 " + pos + "）");
        }
        pos += word.length();
        return value;
    }

    private String excerpt() {
        int end = Math.min(text.length(), pos + 40);
        return text.substring(Math.min(pos, Math.max(0, end - 40)), end).replace('\n', ' ');
    }

    private void skipWs() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw new IllegalArgumentException("JSON 提前结束");
        }
        return text.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char c) {
        if (next() != c) {
            throw new IllegalArgumentException("期望 '" + c + "'（位置 " + (pos - 1) + "）");
        }
    }

    private static String typeName(Object node) {
        if (node == null) {
            return "null";
        }
        if (node instanceof Map) {
            return "对象";
        }
        if (node instanceof List) {
            return "数组";
        }
        if (node instanceof String) {
            return "字符串";
        }
        if (node instanceof Number) {
            return "数字";
        }
        return node.getClass().getSimpleName();
    }
}
