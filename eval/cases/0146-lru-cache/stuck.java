import java.util.*;

class LRUCache {
    private final int capacity;
    private final Map<Integer, Integer> map = new LinkedHashMap<>();

    public LRUCache(int capacity) {
        this.capacity = capacity;
    }

    public int get(int key) {
        return map.getOrDefault(key, -1);
    }

    public void put(int key, int value) {
        if (map.size() >= capacity) {
            Integer oldest = map.keySet().iterator().next();
            map.remove(oldest);
        }
        map.put(key, value);
    }
}
