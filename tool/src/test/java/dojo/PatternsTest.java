package dojo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternsTest {

    private static final String INDEX = """
            # 模式索引

            ## two-pointers · 双指针
            - 本质：两个下标各自单调移动
            - 信号：有序数组找一对 / 原地去重
            - 前置：complexity
            - 子技能：对撞 / 快慢
            - 模板：resources/snippets/two-pointers.java
            - 前置题：125 验证回文串
            - 巩固题：15, 11
            - 链接：https://example.com/a | https://example.com/b
            - 易错：写反 / 漏边界

            ## hash-map · 哈希表
            - 本质：空间换时间
            - 信号：快速判断存在 / 找补数
            - 前置题：1 两数之和
            - 巩固题：49
            - 链接：https://example.com/c
            - 易错：先查后放 / 键选错
            """;

    @TempDir
    Path root;

    private Repo repo() throws IOException {
        Files.createDirectories(root.resolve("problems"));
        Files.createDirectories(root.resolve("solutions"));
        Files.createDirectories(root.resolve("resources/snippets"));
        Files.writeString(root.resolve("resources/patterns.md"), INDEX);
        Files.writeString(root.resolve("resources/snippets/two-pointers.java"), "int l = 0;\n");
        return Repo.at(root);
    }

    @Test
    void parsesEntriesAndFields() throws IOException {
        Repo repo = repo();
        List<Patterns.Pattern> all = Patterns.load(repo);
        assertEquals(2, all.size());
        Patterns.Pattern two = Patterns.find(repo, "two-pointers").orElseThrow();
        assertEquals("双指针", two.name());
        assertEquals("两个下标各自单调移动", two.essence());
        assertEquals(List.of("有序数组找一对", "原地去重"), two.signals());
        assertEquals(List.of("125 验证回文串"), two.before());
        assertEquals(List.of("15", "11"), two.practice());
        assertEquals("https://example.com/a", two.link());
        assertEquals("https://example.com/b", two.backup());
        assertEquals(List.of("写反", "漏边界"), two.pitfalls());
    }

    @Test
    void renderShowsTemplateAndPractice() throws IOException {
        Repo repo = repo();
        String text = Patterns.render(repo, Patterns.find(repo, "two-pointers").orElseThrow());
        assertTrue(text.contains("双指针 · two-pointers"));
        assertTrue(text.contains("模板（resources/snippets/two-pointers.java）"));
        assertTrue(text.contains("│ int l = 0;"));
        assertTrue(text.contains("先练  125 验证回文串"));
        assertTrue(text.contains("备选：https://example.com/b"));
    }

    @Test
    void tailOffersThreeWaysOut() throws IOException {
        Repo repo = repo();
        String tail = Patterns.tail(repo, "two-pointers", false);
        assertTrue(tail.contains("先练：125 验证回文串"));
        assertTrue(tail.contains("dojo learn two-pointers"));
        assertTrue(tail.contains("dojo hint 5"));
        assertFalse(Patterns.tail(repo, "two-pointers", true).contains("dojo hint 5"));
    }

    @Test
    void tailDoesNotPointAtTheSameProblem() throws IOException {
        Repo repo = repo();
        String tail = Patterns.tail(repo, "two-pointers", false, "0125-valid-palindrome");
        assertFalse(tail.contains("先练"), "125 就是当前题，不该再让人去练它");
        assertTrue(tail.contains("dojo learn two-pointers"));
    }

    @Test
    void unknownPatternHasNoTail() throws IOException {
        Repo repo = repo();
        assertNull(Patterns.tail(repo, "unknown", false));
        assertNull(Patterns.tail(repo, "not-in-table", false));
    }

    @Test
    void extractsAndStripsPatternLine() {
        String text = "PATTERN: two-pointers\n先想清楚窗口怎么缩。";
        assertEquals("two-pointers", Patterns.extract(text));
        assertEquals("先想清楚窗口怎么缩。", Patterns.strip(text));
        assertNull(Patterns.extract("PATTERN: made-up\n正文"));
        assertNull(Patterns.extract("没有这一行"));
    }

    @Test
    void fallsBackToProblemTags() throws IOException {
        Repo repo = repo();
        Path dir = root.resolve("problems/0001-two-sum");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("problem.md"),
                "# 1. 两数之和\n\n> 难度：简单 · 标签：数组 / 哈希表\n\n正文\n");
        assertEquals("hash-map", Patterns.fromProblem(repo, "0001-two-sum"));
    }

    @Test
    void remembersWhichPatternWasShown() throws IOException {
        Repo repo = repo();
        assertFalse(Patterns.seen(repo, "two-pointers"));
        Patterns.markSeen(repo, "two-pointers");
        assertTrue(Patterns.seen(repo, "two-pointers"));
        assertTrue(Files.readString(root.resolve(".dojo/learned.log")).startsWith("two-pointers\t"));
    }
}
