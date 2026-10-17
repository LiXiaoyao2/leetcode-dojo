package dojo;

import java.util.ArrayList;
import java.util.List;

/**
 * 题号、包名、类名的唯一换算入口。
 *
 * <p>id 形如 {@code 0001-two-sum}；设计题等非数字题号退化为 {@code x-<slug>}。
 * 包名由 id 推导，所以 Repo 不需要额外记录任何映射。
 */
public final class Naming {

    private Naming() {
    }

    public static String id(String frontendId, String slug) {
        String numeric = frontendId == null ? "" : frontendId.strip();
        if (numeric.matches("\\d+")) {
            return String.format("%04d", Integer.parseInt(numeric)) + "-" + slug;
        }
        return "x-" + slug;
    }

    /** 0001-two-sum → p0001_two_sum；x-min-stack → px_min_stack。 */
    public static String packageOf(String id) {
        int dash = id.indexOf('-');
        String number = dash < 0 ? "x" : id.substring(0, dash);
        String slug = dash < 0 ? id : id.substring(dash + 1);
        return "p" + number + "_" + sanitize(slug);
    }

    /** 0001-two-sum → TwoSumTest。 */
    public static String testClass(String id) {
        int dash = id.indexOf('-');
        String slug = dash < 0 ? id : id.substring(dash + 1);
        return pascal(slug) + "Test";
    }

    /** 0001-two-sum → 0001。 */
    public static String number(String id) {
        int dash = id.indexOf('-');
        return dash < 0 ? id : id.substring(0, dash);
    }

    /** 0001-two-sum → two-sum。 */
    public static String slug(String id) {
        int dash = id.indexOf('-');
        return dash < 0 ? id : id.substring(dash + 1);
    }

    /** 从 URL 或裸 slug 里取出 slug：…/problems/two-sum/description/ → two-sum。 */
    /** 纯数字（或带前导零）当题号处理，例如 new 1 / new 0049。 */
    /** 1-100、1~100、1 – 100 都算区间；只看形如「数字 分隔 数字」。 */
    private static final java.util.regex.Pattern RANGE =
            java.util.regex.Pattern.compile("^\\s*(\\d{1,4})\\s*[-~–—]\\s*(\\d{1,4})\\s*$");

    public static boolean looksRange(String input) {
        return input != null && RANGE.matcher(input).matches();
    }

    /** 区间解析成 {from, to}，含两端；写反了自动换过来。 */
    public static int[] range(String input) {
        java.util.regex.Matcher matcher = RANGE.matcher(input == null ? "" : input);
        if (!matcher.matches()) {
            throw new DojoException("这不是区间：" + input + "（写法如 dojo new 1-100）");
        }
        int a = Integer.parseInt(matcher.group(1));
        int b = Integer.parseInt(matcher.group(2));
        if (a < 1 || b < 1) {
            throw new DojoException("题号从 1 开始，区间写成 " + a + "-" + b + " 没有意义。");
        }
        return a <= b ? new int[]{a, b} : new int[]{b, a};
    }

    public static boolean looksNumeric(String input) {
        String s = input == null ? "" : input.strip();
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    public static String slugOf(String input) {
        String s = input.strip();
        int marker = s.indexOf("/problems/");
        if (marker >= 0) {
            s = s.substring(marker + "/problems/".length());
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        int query = s.indexOf('?');
        if (query >= 0) {
            s = s.substring(0, query);
        }
        return s.strip();
    }

    private static String sanitize(String slug) {
        StringBuilder sb = new StringBuilder();
        for (char c : slug.toLowerCase().toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.toString();
    }

    private static String pascal(String slug) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : slug.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                parts.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        String name = out.length() == 0 ? "Problem" : out.toString();
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            name = "P" + name;
        }
        return name;
    }
}
