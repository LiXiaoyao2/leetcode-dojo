package kit;

import java.util.ArrayList;
import java.util.List;

/** 单链表的构造与比较工具：口算不了指针，但看得懂数组。 */
public final class ListNodes {

    private ListNodes() {
    }

    /** [1,2,3] → 1→2→3；空数组按 LeetCode 约定返回 null。 */
    public static ListNode of(int... vals) {
        if (vals.length == 0) {
            return null;
        }
        ListNode head = new ListNode(vals[vals.length - 1]);
        for (int i = vals.length - 2; i >= 0; i--) {
            head = new ListNode(vals[i], head);
        }
        return head;
    }

    /** 1→2→3 → [1,2,3]；null → []。 */
    public static int[] toArray(ListNode head) {
        List<Integer> vals = new ArrayList<>();
        for (ListNode p = head; p != null; p = p.next) {
            vals.add(p.val);
        }
        return vals.stream().mapToInt(Integer::intValue).toArray();
    }
}
