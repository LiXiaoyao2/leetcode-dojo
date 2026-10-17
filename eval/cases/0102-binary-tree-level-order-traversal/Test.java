package p0102_binary_tree_level_order_traversal;

import java.util.*;

import org.junit.jupiter.api.Test;

import kit.TreeNode;
import kit.TreeNodes;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class BinaryTreeLevelOrderTraversalTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        TreeNode root = TreeNodes.of(new Integer[]{3, 9, 20, null, null, 15, 7});
        List<List<Integer>> actual = solution.levelOrder(root);
        assertEquals(List.<List<Integer>>of(List.<Integer>of(3), List.<Integer>of(9, 20), List.<Integer>of(15, 7)), actual);
    }

    @Test
    void officialExample2() {
        TreeNode root = TreeNodes.of(new Integer[]{1});
        List<List<Integer>> actual = solution.levelOrder(root);
        assertEquals(List.<List<Integer>>of(List.<Integer>of(1)), actual);
    }

    @Test
    void officialExample3() {
        TreeNode root = TreeNodes.of(new Integer[]{});
        List<List<Integer>> actual = solution.levelOrder(root);
        assertEquals(List.<List<Integer>>of(), actual);
    }
}
