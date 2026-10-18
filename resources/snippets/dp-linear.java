// 线性 DP：dp[i] 表示「以 i 结尾」或「前 i 个」的最优值
int[] dp = new int[n + 1];
dp[0] = 0;                       // 边界：空集
for (int i = 1; i <= n; i++) {
    dp[i] = dp[i - 1];           // 不选 i
    if (/* 选 i 合法 */) {
        dp[i] = Math.max(dp[i], dp[i - 1] + value(i));
    }
}
return dp[n];
