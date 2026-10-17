package p0300_longest_increasing_subsequence;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class LongestIncreasingSubsequenceTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        int[] nums = new int[]{10, 9, 2, 5, 3, 7, 101, 18};
        int actual = solution.lengthOfLIS(nums);
        assertEquals(4, actual);
    }

    @Test
    void officialExample2() {
        int[] nums = new int[]{0, 1, 0, 3, 2, 3};
        int actual = solution.lengthOfLIS(nums);
        assertEquals(4, actual);
    }

    @Test
    void officialExample3() {
        int[] nums = new int[]{7, 7, 7, 7, 7, 7, 7};
        int actual = solution.lengthOfLIS(nums);
        assertEquals(1, actual);
    }
}
