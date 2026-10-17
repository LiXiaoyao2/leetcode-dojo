package dojo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlTest {

    @Test
    void examplesBecomeFencedBlocks() {
        String markdown = Html.toMarkdown("""
                <p>给定一个整数数组 <code>nums</code> 和 <strong>target</strong>。</p>
                <pre>
                <strong>输入：</strong>nums = [2,7,11,15], target = 9
                <strong>输出：</strong>[0,1]
                <strong>解释：</strong>因为 nums[0] + nums[1] == 9
                </pre>
                """);
        assertTrue(markdown.contains("```"), markdown);
        assertTrue(markdown.contains("`nums`"), markdown);
        assertTrue(markdown.contains("**target**"), markdown);
        assertTrue(markdown.contains("输入：nums = [2,7,11,15], target = 9"), markdown);
        assertTrue(markdown.contains("输出：[0,1]"), markdown);
        assertFalse(markdown.contains("<strong>"), markdown);
        assertTrue(markdown.startsWith("给定一个整数数组"), markdown);
    }

    @Test
    void keepsPreLineBreaks() {
        String markdown = Html.toMarkdown("<pre>a\n\nb<br>c</pre>");
        assertTrue(markdown.contains("a\n\nb\nc"), markdown);
    }

    @Test
    void rendersListsAndEntities() {
        String markdown = Html.toMarkdown("""
                <ul><li>2 &lt;= n &lt;= 10<sup>4</sup></li><li>答案唯一</li></ul>
                """);
        assertTrue(markdown.contains("- 2 <= n <= 10^4"), markdown);
        assertTrue(markdown.contains("- 答案唯一"), markdown);
    }

    @Test
    void imageBecomesMarkdownNotItalic() {
        assertEquals("![图](https://x/a.png)",
                Html.toMarkdown("<p><img alt=\"\" src=\"https://x/a.png\" /></p>"));
        assertTrue(Html.toMarkdown("<p><i>斜</i></p>").contains("_斜_"));
        assertTrue(Html.toMarkdown("<p><img alt=\"示意图\" src=\"https://x/b.png\"/></p>")
                .contains("![示意图](https://x/b.png)"));
    }

    @Test
    void unescapesNumericEntities() {
        assertEquals("A中", Html.unescape("&#65;&#20013;"));
        assertEquals("a&b", Html.unescape(Html.stripTags("<b>a</b>&amp;b")));
    }
}
