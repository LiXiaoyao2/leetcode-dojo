package p0200_number_of_islands;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class NumberOfIslandsTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        char[][] grid = new char[][]{new char[]{'1', '1', '1', '1', '0'}, new char[]{'1', '1', '0', '1', '0'}, new char[]{'1', '1', '0', '0', '0'}, new char[]{'0', '0', '0', '0', '0'}};
        int actual = solution.numIslands(grid);
        assertEquals(1, actual);
    }

    @Test
    void officialExample2() {
        char[][] grid = new char[][]{new char[]{'1', '1', '0', '0', '0'}, new char[]{'1', '1', '0', '0', '0'}, new char[]{'0', '0', '1', '0', '0'}, new char[]{'0', '0', '0', '1', '1'}};
        int actual = solution.numIslands(grid);
        assertEquals(3, actual);
    }
}
