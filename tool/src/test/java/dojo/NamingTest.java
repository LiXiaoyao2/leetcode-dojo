package dojo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingTest {

    @Test
    void buildsIds() {
        assertEquals("0001-two-sum", Naming.id("1", "two-sum"));
        assertEquals("0042-trapping-rain-water", Naming.id("42", "trapping-rain-water"));
        assertEquals("x-min-stack", Naming.id(null, "min-stack"));
        assertEquals("x-lru-cache", Naming.id(null, "lru-cache"));
    }

    @Test
    void buildsPackages() {
        assertEquals("p0001_two_sum", Naming.packageOf("0001-two-sum"));
        assertEquals("p0146_lru_cache", Naming.packageOf("0146-lru-cache"));
        assertEquals("px_min_stack", Naming.packageOf("x-min-stack"));
        assertEquals("p0003_longest_substring_without_repeating_characters",
                Naming.packageOf("0003-longest-substring-without-repeating-characters"));
    }

    @Test
    void buildsTestClasses() {
        assertEquals("TwoSumTest", Naming.testClass("0001-two-sum"));
        assertEquals("AddTwoNumbersTest", Naming.testClass("0002-add-two-numbers"));
        assertEquals("P3sumTest", Naming.testClass("x-3sum"));
        assertEquals("LruCacheTest", Naming.testClass("0146-lru-cache"));
    }

    @Test
    void splitsId() {
        assertEquals("0001", Naming.number("0001-two-sum"));
        assertEquals("two-sum", Naming.slug("0001-two-sum"));
        assertEquals("x", Naming.number("x-min-stack"));
        assertEquals("min-stack", Naming.slug("x-min-stack"));
    }

    @Test
    void extractsSlugFromUrls() {
        assertEquals("two-sum", Naming.slugOf("two-sum"));
        assertEquals("two-sum", Naming.slugOf("  two-sum  "));
        assertEquals("two-sum", Naming.slugOf("https://leetcode.cn/problems/two-sum/"));
        assertEquals("two-sum", Naming.slugOf("https://leetcode.cn/problems/two-sum/description/?envType=study-plan"));
        assertEquals("two-sum", Naming.slugOf("https://leetcode.com/problems/two-sum/solutions/"));
        assertEquals("two-sum", Naming.slugOf("/problems/two-sum/"));
    }

    @Test
    void rangeRecognizesCommonSpellings() {
        assertTrue(Naming.looksRange("1-100"));
        assertTrue(Naming.looksRange("1 ~ 100"));
        assertTrue(Naming.looksRange(" 1 – 5 "));
        assertFalse(Naming.looksRange("1"));
        assertFalse(Naming.looksRange("two-sum"));
        assertFalse(Naming.looksRange("1-2-3"));
    }

    @Test
    void rangeParsesInclusiveAndSwapsReversed() {
        assertArrayEquals(new int[]{1, 100}, Naming.range("1-100"));
        assertArrayEquals(new int[]{3, 3}, Naming.range("3~3"));
        assertArrayEquals(new int[]{2, 9}, Naming.range("9-2"));
        assertThrows(DojoException.class, () -> Naming.range("0-5"));
        assertThrows(DojoException.class, () -> Naming.range("1-"));
    }
}
