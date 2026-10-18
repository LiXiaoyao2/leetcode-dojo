// 自底向上：返回值是「这棵子树对父节点的贡献」
private int dfs(TreeNode node) {
    if (node == null) {
        return 0;
    }
    int left = dfs(node.left);
    int right = dfs(node.right);
    // 在这里用 left / right / node.val 组合出答案
    return Math.max(left, right) + 1;
}

// 层序：进循环前先固定这一层的长度
Deque<TreeNode> queue = new LinkedList<>();
queue.add(root);
while (!queue.isEmpty()) {
    int size = queue.size();
    for (int i = 0; i < size; i++) {
        TreeNode node = queue.poll();
        // 处理这一层
        if (node.left != null) queue.add(node.left);
        if (node.right != null) queue.add(node.right);
    }
}
