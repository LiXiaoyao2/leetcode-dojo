package p0001_two_sum;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class TwoSumTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        int[] nums = new int[]{2, 7, 11, 15};
        int target = 9;
        int[] actual = solution.twoSum(nums, target);
        int[] expected = new int[]{0, 1};
        int[] got = actual.clone();
        Arrays.sort(expected);
        Arrays.sort(got);
        assertArrayEquals(expected, got);
    }

    @Test
    void officialExample2() {
        int[] nums = new int[]{3, 2, 4};
        int target = 6;
        int[] actual = solution.twoSum(nums, target);
        int[] expected = new int[]{1, 2};
        int[] got = actual.clone();
        Arrays.sort(expected);
        Arrays.sort(got);
        assertArrayEquals(expected, got);
    }

    @Test
    void officialExample3() {
        int[] nums = new int[]{3, 3};
        int target = 6;
        int[] actual = solution.twoSum(nums, target);
        int[] expected = new int[]{0, 1};
        int[] got = actual.clone();
        Arrays.sort(expected);
        Arrays.sort(got);
        assertArrayEquals(expected, got);
    }
}
