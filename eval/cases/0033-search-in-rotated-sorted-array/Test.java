package p0033_search_in_rotated_sorted_array;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class SearchInRotatedSortedArrayTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        int[] nums = new int[]{4, 5, 6, 7, 0, 1, 2};
        int target = 0;
        int actual = solution.search(nums, target);
        assertEquals(4, actual);
    }

    @Test
    void officialExample2() {
        int[] nums = new int[]{4, 5, 6, 7, 0, 1, 2};
        int target = 3;
        int actual = solution.search(nums, target);
        assertEquals(-1, actual);
    }

    @Test
    void officialExample3() {
        int[] nums = new int[]{1};
        int target = 0;
        int actual = solution.search(nums, target);
        assertEquals(-1, actual);
    }
}
