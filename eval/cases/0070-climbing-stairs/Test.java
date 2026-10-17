package p0070_climbing_stairs;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class ClimbingStairsTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        int n = 2;
        int actual = solution.climbStairs(n);
        assertEquals(2, actual);
    }

    @Test
    void officialExample2() {
        int n = 3;
        int actual = solution.climbStairs(n);
        assertEquals(3, actual);
    }
}
