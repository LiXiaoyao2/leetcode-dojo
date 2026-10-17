package p0053_maximum_subarray;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class MaximumSubarrayTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        int[] nums = new int[]{-2, 1, -3, 4, -1, 2, 1, -5, 4};
        int actual = solution.maxSubArray(nums);
        assertEquals(6, actual);
    }

    @Test
    void officialExample2() {
        int[] nums = new int[]{1};
        int actual = solution.maxSubArray(nums);
        assertEquals(1, actual);
    }

    @Test
    void officialExample3() {
        int[] nums = new int[]{5, 4, -1, 7, 8};
        int actual = solution.maxSubArray(nums);
        assertEquals(23, actual);
    }
}
