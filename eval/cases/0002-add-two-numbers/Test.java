package p0002_add_two_numbers;

import java.util.*;

import org.junit.jupiter.api.Test;

import kit.ListNode;
import kit.ListNodes;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class AddTwoNumbersTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        ListNode l1 = ListNodes.of(new int[]{2, 4, 3});
        ListNode l2 = ListNodes.of(new int[]{5, 6, 4});
        ListNode actual = solution.addTwoNumbers(l1, l2);
        assertArrayEquals(new int[]{7, 0, 8}, ListNodes.toArray(actual));
    }

    @Test
    void officialExample2() {
        ListNode l1 = ListNodes.of(new int[]{0});
        ListNode l2 = ListNodes.of(new int[]{0});
        ListNode actual = solution.addTwoNumbers(l1, l2);
        assertArrayEquals(new int[]{0}, ListNodes.toArray(actual));
    }

    @Test
    void officialExample3() {
        ListNode l1 = ListNodes.of(new int[]{9, 9, 9, 9, 9, 9, 9});
        ListNode l2 = ListNodes.of(new int[]{9, 9, 9, 9});
        ListNode actual = solution.addTwoNumbers(l1, l2);
        assertArrayEquals(new int[]{8, 9, 9, 9, 0, 0, 0, 1}, ListNodes.toArray(actual));
    }
}
