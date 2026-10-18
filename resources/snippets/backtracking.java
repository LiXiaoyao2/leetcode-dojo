// 回溯：进入时做选择，退出时撤销
private void dfs(int start) {
    if (/* 到达叶子 */) {
        answer.add(new ArrayList<>(path));
        return;
    }
    for (int i = start; i < n; i++) {
        path.add(a[i]);      // 做选择
        dfs(i + 1);          // 进入下一层
        path.remove(path.size() - 1);  // 撤销
    }
}
