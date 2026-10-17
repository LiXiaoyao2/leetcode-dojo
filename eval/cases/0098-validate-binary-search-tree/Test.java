package p0098_validate_binary_search_tree;

import java.util.*;

import org.junit.jupiter.api.Test;

import kit.TreeNode;
import kit.TreeNodes;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class ValidateBinarySearchTreeTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        TreeNode root = TreeNodes.of(new Integer[]{2, 1, 3});
        boolean actual = solution.isValidBST(root);
        assertEquals(true, actual);
    }

    @Test
    void officialExample2() {
        TreeNode root = TreeNodes.of(new Integer[]{5, 1, 4, null, null, 3, 6});
        boolean actual = solution.isValidBST(root);
        assertEquals(false, actual);
    }
}
