package p0015_3sum;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class P3sumTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        int[] nums = new int[]{-1, 0, 1, 2, -1, -4};
        List<List<Integer>> actual = solution.threeSum(nums);
        assertEquals(normInts(List.<List<Integer>>of(List.<Integer>of(-1, -1, 2), List.<Integer>of(-1, 0, 1))), normInts(actual));
    }

    @Test
    void officialExample2() {
        int[] nums = new int[]{0, 1, 1};
        List<List<Integer>> actual = solution.threeSum(nums);
        assertEquals(normInts(List.<List<Integer>>of()), normInts(actual));
    }

    @Test
    void officialExample3() {
        int[] nums = new int[]{0, 0, 0};
        List<List<Integer>> actual = solution.threeSum(nums);
        assertEquals(normInts(List.<List<Integer>>of(List.<Integer>of(0, 0, 0))), normInts(actual));
    }

    private static List<List<Integer>> normInts(List<List<Integer>> value) {
        List<List<Integer>> out = new ArrayList<>();
        for (List<Integer> inner : value) {
            List<Integer> copy = new ArrayList<>(inner);
            Collections.sort(copy);
            out.add(copy);
        }
        out.sort(Comparator.comparing(Object::toString));
        return out;
    }
}
