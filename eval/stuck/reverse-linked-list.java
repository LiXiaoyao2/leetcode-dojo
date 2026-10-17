import kit.ListNode;

class Solution {
    public ListNode reverseList(ListNode head) {
        ListNode prev = null;
        ListNode cur = head;
        while (cur != null) {
            ListNode next = cur.next;
            prev = cur;
            cur = next;
        }
        return prev;
    }
}
