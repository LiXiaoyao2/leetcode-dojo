package p0021_merge_two_sorted_lists;

import java.util.*;

import org.junit.jupiter.api.Test;

import kit.ListNode;
import kit.ListNodes;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class MergeTwoSortedListsTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        ListNode list1 = ListNodes.of(new int[]{1, 2, 4});
        ListNode list2 = ListNodes.of(new int[]{1, 3, 4});
        ListNode actual = solution.mergeTwoLists(list1, list2);
        assertArrayEquals(new int[]{1, 1, 2, 3, 4, 4}, ListNodes.toArray(actual));
    }

    @Test
    void officialExample2() {
        ListNode list1 = ListNodes.of(new int[]{});
        ListNode list2 = ListNodes.of(new int[]{});
        ListNode actual = solution.mergeTwoLists(list1, list2);
        assertArrayEquals(new int[]{}, ListNodes.toArray(actual));
    }

    @Test
    void officialExample3() {
        ListNode list1 = ListNodes.of(new int[]{});
        ListNode list2 = ListNodes.of(new int[]{0});
        ListNode actual = solution.mergeTwoLists(list1, list2);
        assertArrayEquals(new int[]{0}, ListNodes.toArray(actual));
    }
}
