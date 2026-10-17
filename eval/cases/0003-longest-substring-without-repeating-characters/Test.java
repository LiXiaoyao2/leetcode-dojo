package p0003_longest_substring_without_repeating_characters;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class LongestSubstringWithoutRepeatingCharactersTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        String s = "abcabcbb";
        int actual = solution.lengthOfLongestSubstring(s);
        assertEquals(3, actual);
    }

    @Test
    void officialExample2() {
        String s = "bbbbb";
        int actual = solution.lengthOfLongestSubstring(s);
        assertEquals(1, actual);
    }

    @Test
    void officialExample3() {
        String s = "pwwkew";
        int actual = solution.lengthOfLongestSubstring(s);
        assertEquals(3, actual);
    }
}
