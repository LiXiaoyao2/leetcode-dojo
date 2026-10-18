// 哈希表：先查后放，保证同一个元素不会被用两次
Map<Integer, Integer> seen = new HashMap<>();
for (int i = 0; i < a.length; i++) {
    int need = target - a[i];
    if (seen.containsKey(need)) {
        return new int[]{seen.get(need), i};
    }
    seen.put(a[i], i);
}
return new int[]{-1, -1};
