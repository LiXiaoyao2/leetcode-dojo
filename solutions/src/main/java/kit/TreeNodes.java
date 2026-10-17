package kit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 二叉树的构造与序列化工具。
 * 输入格式与 LeetCode 一致：层序，null 表示空位（例如 [4,2,7,1,3,6,9] 或 [1,null,2,3]）。
 */
public final class TreeNodes {

    private TreeNodes() {
    }

    public static TreeNode of(Integer... vals) {
        if (vals == null || vals.length == 0 || vals[0] == null) {
            return null;
        }
        TreeNode root = new TreeNode(vals[0]);
        Deque<TreeNode> queue = new ArrayDeque<>();
        queue.add(root);
        int i = 1;
        while (i < vals.length && !queue.isEmpty()) {
            TreeNode parent = queue.poll();
            if (i < vals.length && vals[i] != null) {
                parent.left = new TreeNode(vals[i]);
                queue.add(parent.left);
            }
            i++;
            if (i < vals.length && vals[i] != null) {
                parent.right = new TreeNode(vals[i]);
                queue.add(parent.right);
            }
            i++;
        }
        return root;
    }

    /** 层序序列化，去掉末尾的 null（与 LeetCode 的显示格式一致）。 */
    public static Integer[] toArray(TreeNode root) {
        List<Integer> out = new ArrayList<>();
        if (root != null) {
            Deque<TreeNode> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                TreeNode node = queue.poll();
                if (node == null) {
                    out.add(null);
                } else {
                    out.add(node.val);
                    queue.add(node.left);
                    queue.add(node.right);
                }
            }
            while (!out.isEmpty() && out.getLast() == null) {
                out.removeLast();
            }
        }
        return out.toArray(new Integer[0]);
    }
}
