package kit;

/**
 * LeetCode 单链表节点。字段名与官方模板一致（val / next），
 * 所以题目模板里写 ListNode 的地方可以直接用 kit 里的这个类。
 */
public class ListNode {
    public int val;
    public ListNode next;

    public ListNode() {
    }

    public ListNode(int val) {
        this.val = val;
    }

    public ListNode(int val, ListNode next) {
        this.val = val;
        this.next = next;
    }
}
