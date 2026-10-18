// 对撞指针：有序数组里找一对，和太大就右移左端，太小就左移右端
int l = 0, r = a.length - 1;
while (l < r) {
    int sum = a[l] + a[r];
    if (sum == target) {
        return new int[]{l, r};
    }
    if (sum < target) {
        l++;
    } else {
        r--;
    }
}
return new int[]{-1, -1};
