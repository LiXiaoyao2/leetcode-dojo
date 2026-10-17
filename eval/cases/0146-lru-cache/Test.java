package p0146_lru_cache;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class LruCacheTest {

    @Test
    void officialExample1() {
        LRUCache obj = new LRUCache(2);
        obj.put(1, 1);
        obj.put(2, 2);
        assertEquals(1, obj.get(1));
        obj.put(3, 3);
        assertEquals(-1, obj.get(2));
        obj.put(4, 4);
        assertEquals(-1, obj.get(1));
        assertEquals(3, obj.get(3));
        assertEquals(4, obj.get(4));
    }
}
