package p0011_container_with_most_water;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class ContainerWithMostWaterTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        int[] height = new int[]{1, 8, 6, 2, 5, 4, 8, 3, 7};
        int actual = solution.maxArea(height);
        assertEquals(49, actual);
    }

    @Test
    void officialExample2() {
        int[] height = new int[]{1, 1};
        int actual = solution.maxArea(height);
        assertEquals(1, actual);
    }
}
