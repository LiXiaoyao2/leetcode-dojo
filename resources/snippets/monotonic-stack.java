// 单调栈：栈里存下标，弹出时就能确定「左右第一个更大/更小」
Deque<Integer> st = new ArrayDeque<>();
for (int i = 0; i < a.length; i++) {
    while (!st.isEmpty() && a[st.peek()] < a[i]) {
        int j = st.pop();       // a[j] 右侧第一个更大元素就是 i
        // 在这里结算以 j 为边界的答案
    }
    st.push(i);
}
