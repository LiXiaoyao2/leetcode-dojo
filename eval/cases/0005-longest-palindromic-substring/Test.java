package p0005_longest_palindromic_substring;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class LongestPalindromicSubstringTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        String s = "babad";
        String actual = solution.longestPalindrome(s);
        assertEquals("bab", actual);
    }

    @Test
    void officialExample2() {
        String s = "cbbd";
        String actual = solution.longestPalindrome(s);
        assertEquals("bb", actual);
    }
}
