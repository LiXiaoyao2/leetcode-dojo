// 变长窗口：右端扩张，条件被破坏时左端收缩
int l = 0, best = 0;
for (int r = 0; r < a.length; r++) {
    // 把 a[r] 加进窗口
    while (/* 窗口不满足条件 */) {
        // 把 a[l] 移出窗口
        l++;
    }
    best = Math.max(best, r - l + 1);
}
return best;
