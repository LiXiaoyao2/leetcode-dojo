package dojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvalMetricsTest {

    @Test
    void identicalTextsAreFullySimilar() {
        assertEquals(1.0, EvalMetrics.similarity("哈希表查补数", "哈希表查补数"));
    }

    @Test
    void unrelatedTextsAreNotSimilar() {
        assertTrue(EvalMetrics.similarity("哈希表查补数", "双指针从两端收缩") < 0.2);
    }

    @Test
    void repeatsCatchesCopyOfEarlierLevel() {
        assertTrue(EvalMetrics.repeats("把见过的值放进哈希表，查 target - nums[i]", List.of("把见过的值放进哈希表，查 target - nums[i]")));
    }

    @Test
    void repeatsAllowsFreshWording() {
        assertFalse(EvalMetrics.repeats("双指针从两端往中间收缩", List.of("把见过的值放进哈希表，查补数")));
    }

    @Test
    void codeInEarlyLevelsIsLeak() {
        assertTrue(EvalMetrics.leaks("class Solution { public int f() { return 1; } }", 3));
        assertFalse(EvalMetrics.leaks("class Solution { public int f() { return 1; } }", 5));
    }

    @Test
    void overLimitIsTooLong() {
        assertTrue(EvalMetrics.tooLong("字".repeat(200), 1));
        assertFalse(EvalMetrics.tooLong("字".repeat(100), 1));
    }

    @Test
    void statIgnoresFailedCalls() {
        EvalMetrics.Cell ok = new EvalMetrics.Cell(1, "问题一？问题二？", 9, false, false, false, 1000, 50, false, false);
        EvalMetrics.Cell bad = new EvalMetrics.Cell(2, "", 0, false, false, false, 0, 0, true, false);
        EvalMetrics.Stat stat = EvalMetrics.Stat.of(List.of(ok, bad));
        assertEquals(2, stat.hints());
        assertEquals(1, stat.failed());
        assertEquals(1000, stat.medianMillis());
    }
}
