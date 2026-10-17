package dojo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LadderTest {

    @Test
    void levelOneFirstThenAdvances() {
        assertEquals(1, Ladder.Level.next(List.of()).number());
        assertEquals(3, Ladder.Level.next(List.of(
                new Ladder.Turn(1, "澄清", "a"),
                new Ladder.Turn(2, "方向", "b"))).number());
        assertEquals(5, Ladder.Level.next(List.of(new Ladder.Turn(5, "题解", "c"))).number());
    }

    @Test
    void readsHistorySections() throws IOException {
        Path file = Files.createTempFile("hints", ".md");
        Files.writeString(file, """
                # 提示记录 · 0001-two-sum

                ## L1 · 澄清 · 2026-10-18 01:00
                > 卡点：不知道怎么处理重复
                先说边界：数组可以为空吗？

                ## L2 · 方向 · 2026-10-18 01:10
                哈希表。
                """);
        List<Ladder.Turn> turns = Ladder.readHistory(file);
        assertEquals(2, turns.size());
        assertEquals(1, turns.get(0).level());
        assertEquals(2, turns.get(1).level());
        assertTrue(turns.get(0).body().contains("不知道怎么处理重复"), turns.get(0).body());
        assertTrue(turns.get(1).body().contains("哈希表"), turns.get(1).body());
        assertTrue(turns.get(1).header().startsWith("方向"), turns.get(1).header());
    }

    @Test
    void missingFileMeansEmptyHistory() {
        assertTrue(Ladder.readHistory(Path.of("/tmp/dojo-does-not-exist-hints.md")).isEmpty());
    }

    @Test
    void appendsWithHeaderAndNote() throws IOException {
        Path file = Files.createTempFile("hints", ".md");
        Files.delete(file);
        Ladder.append(file, Ladder.Level.of(2), "卡在重复元素", "用哈希表存补数的下标。");
        String text = Files.readString(file);
        assertTrue(text.contains("# 提示记录"), text);
        assertTrue(text.contains("## L2 · 方向"), text);
        assertTrue(text.contains("> 卡点：卡在重复元素"), text);
        assertTrue(text.contains("用哈希表存补数的下标。"), text);

        Ladder.append(file, Ladder.Level.of(3), null, "第二条");
        List<Ladder.Turn> turns = Ladder.readHistory(file);
        assertEquals(2, turns.size());
        assertFalse(turns.get(1).body().contains("卡点："), turns.get(1).body());
    }

    @Test
    void promptCarriesEverythingTheModelNeeds() {
        List<Ladder.Turn> history = List.of(new Ladder.Turn(1, "澄清 · 2026-10-18 01:00", "数组为空怎么办？"));
        String prompt = Ladder.userPrompt("两数之和的题面", "1  int[] twoSum(int[] nums, int target) {",
                "Tests run: 1, Failures: 1", history, Ladder.Level.of(2), "我只会暴力");
        assertTrue(prompt.contains("两数之和的题面"), prompt);
        assertTrue(prompt.contains("twoSum"), prompt);
        assertTrue(prompt.contains("Tests run: 1, Failures: 1"), prompt);
        assertTrue(prompt.contains("数组为空怎么办？"), prompt);
        assertTrue(prompt.contains("这一档"), prompt);
        assertTrue(prompt.contains("L2 · 方向"), prompt);
        assertTrue(prompt.contains("我只会暴力"), prompt);
    }

    @Test
    void tellsTheModelWhenNothingHasBeenTried() {
        String prompt = Ladder.userPrompt("题面", null, null, List.of(), Ladder.Level.of(1), null);
        assertTrue(prompt.contains("还没动笔"), prompt);
        assertTrue(prompt.contains("还没跑过"), prompt);
        assertTrue(prompt.contains("这是第一档"), prompt);
    }

    @Test
    void detectsFullSolutions() {
        assertTrue(Ladder.looksLikeJava("class Solution {\n}"));
        assertTrue(Ladder.looksLikeJava("    public int[] twoSum(int[] nums, int target) {\n        return null;\n    }"));
        assertFalse(Ladder.looksLikeJava("先想清楚：哈希表存的是「补数」还是「下标」？"));
        assertFalse(Ladder.looksLikeJava("1. 建一个 HashMap\n2. 遍历数组\n3. 查 target - x"));
    }
}
