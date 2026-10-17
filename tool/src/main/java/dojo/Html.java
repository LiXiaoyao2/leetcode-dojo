package dojo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 LeetCode 的题面 HTML 转成 markdown。
 *
 * <p>只处理题面里真实出现的标签；遇到不认识的标签就剥掉，宁可少一点样式，
 * 也不要让 {@code &lt;sup&gt;} 这种东西污染发给模型的题面。
 */
public final class Html {

    private static final Pattern PRE = Pattern.compile("(?is)<pre>(.*?)</pre>");

    private Html() {
    }

    public static String toMarkdown(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String s = html.replace("\r\n", "\n").replace("\r", "\n");

        // 1. 先把 <pre> 挖出来，保证代码块里的空行/缩进不被后续规则破坏。
        Matcher matcher = PRE.matcher(s);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String inner = matcher.group(1)
                    .replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)</(p|div|li)>", "\n");
            inner = stripTags(inner);
            inner = unescape(inner);
            inner = trimLines(inner);
            matcher.appendReplacement(out, Matcher.quoteReplacement("\n```\n" + inner + "\n```\n"));
        }
        matcher.appendTail(out);
        s = out.toString();

        // 2. 剩下的行内标签。
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</p>", "\n\n");
        s = s.replaceAll("(?i)<p[^>]*>", "");
        s = s.replaceAll("(?i)<ul[^>]*>", "\n");
        s = s.replaceAll("(?i)</ul>", "\n");
        s = s.replaceAll("(?i)<ol[^>]*>", "\n");
        s = s.replaceAll("(?i)</ol>", "\n");
        s = s.replaceAll("(?i)<li[^>]*>", "- ");
        s = s.replaceAll("(?m)^[ \\t]+(- )", "$1");
        s = s.replaceAll("(?i)</li>", "\n");
        s = s.replaceAll("(?i)<sup[^>]*>", "^");
        s = s.replaceAll("(?i)</sup>", "");
        s = s.replaceAll("(?i)<sub[^>]*>", "_");
        s = s.replaceAll("(?i)</sub>", "");
        s = s.replaceAll("(?i)<code[^>]*>", "`");
        s = s.replaceAll("(?i)</code>", "`");
        // 注意边界：<(em|i)[^>]*> 会把 <img ...> 也吃掉（<i 是 <img 的前缀），
        // <b[^>]*> 会误伤 <br>、<blockquote>，所以这里都要求标签名后跟空白或结尾。
        s = s.replaceAll("(?i)<(strong|b)(\\s[^>]*)?>", "**");
        s = s.replaceAll("(?i)</(strong|b)>", "**");
        s = s.replaceAll("(?i)<(em|i)(\\s[^>]*)?>", "_");
        s = s.replaceAll("(?i)</(em|i)>", "_");
        s = imagesToMarkdown(s);

        s = stripTags(s);
        s = unescape(s);

        // 3. 收尾：行尾空格、三连空行、首尾空行。
        s = s.replaceAll("[ \t]+\n", "\n");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.strip();
    }

    private static final Pattern IMG = Pattern.compile("(?i)<img\\b[^>]*>");

    /** 题面里的图（数据结构示意图）不能丢，转成 markdown 图片；没 src 就退化成 alt。 */
    static String imagesToMarkdown(String s) {
        Matcher matcher = IMG.matcher(s);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String tag = matcher.group();
            String alt = attr(tag, "alt").strip();
            String src = attr(tag, "src").strip();
            String replacement;
            if (src.isEmpty()) {
                replacement = alt;
            } else {
                replacement = "![" + (alt.isEmpty() ? "图" : alt) + "](" + src + ")";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String attr(String tag, String name) {
        Matcher matcher = Pattern.compile("(?i)\\b" + name + "\\s*=\\s*\"([^\"]*)\"").matcher(tag);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static String stripTags(String s) {
        return s.replaceAll("(?s)<[^>]+>", "");
    }

    public static String unescape(String s) {
        String out = s
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
        Matcher numeric = Pattern.compile("&#(\\d+);").matcher(out);
        StringBuilder sb = new StringBuilder();
        while (numeric.find()) {
            numeric.appendReplacement(sb, Matcher.quoteReplacement(
                    new String(Character.toChars(Integer.parseInt(numeric.group(1))))));
        }
        numeric.appendTail(sb);
        return sb.toString();
    }

    private static String trimLines(String s) {
        String[] lines = s.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.stripTrailing()).append('\n');
        }
        return sb.toString().strip();
    }
}
