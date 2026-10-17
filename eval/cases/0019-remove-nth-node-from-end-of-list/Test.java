package p0019_remove_nth_node_from_end_of_list;

import java.util.*;

import org.junit.jupiter.api.Test;

import kit.ListNode;
import kit.ListNodes;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class RemoveNthNodeFromEndOfListTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        ListNode head = ListNodes.of(new int[]{1, 2, 3, 4, 5});
        int n = 2;
        ListNode actual = solution.removeNthFromEnd(head, n);
        assertArrayEquals(new int[]{1, 2, 3, 5}, ListNodes.toArray(actual));
    }

    @Test
    void officialExample2() {
        ListNode head = ListNodes.of(new int[]{1});
        int n = 1;
        ListNode actual = solution.removeNthFromEnd(head, n);
        assertArrayEquals(new int[]{}, ListNodes.toArray(actual));
    }

    @Test
    void officialExample3() {
        ListNode head = ListNodes.of(new int[]{1, 2});
        int n = 1;
        ListNode actual = solution.removeNthFromEnd(head, n);
        assertArrayEquals(new int[]{1}, ListNodes.toArray(actual));
    }
}
