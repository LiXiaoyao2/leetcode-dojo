package p0206_reverse_linked_list;

import java.util.*;

import org.junit.jupiter.api.Test;

import kit.ListNode;
import kit.ListNodes;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class ReverseLinkedListTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        ListNode head = ListNodes.of(new int[]{1, 2, 3, 4, 5});
        ListNode actual = solution.reverseList(head);
        assertArrayEquals(new int[]{5, 4, 3, 2, 1}, ListNodes.toArray(actual));
    }

    @Test
    void officialExample2() {
        ListNode head = ListNodes.of(new int[]{1, 2});
        ListNode actual = solution.reverseList(head);
        assertArrayEquals(new int[]{2, 1}, ListNodes.toArray(actual));
    }

    @Test
    void officialExample3() {
        ListNode head = ListNodes.of(new int[]{});
        ListNode actual = solution.reverseList(head);
        assertArrayEquals(new int[]{}, ListNodes.toArray(actual));
    }
}
