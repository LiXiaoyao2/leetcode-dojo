package p0215_kth_largest_element_in_an_array;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class KthLargestElementInAnArrayTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        int[] nums = new int[]{3, 2, 1, 5, 6, 4};
        int k = 2;
        int actual = solution.findKthLargest(nums, k);
        assertEquals(5, actual);
    }

    @Test
    void officialExample2() {
        int[] nums = new int[]{3, 2, 3, 1, 2, 4, 5, 5, 6};
        int k = 4;
        int actual = solution.findKthLargest(nums, k);
        assertEquals(4, actual);
    }
}
