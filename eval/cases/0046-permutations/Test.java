package p0046_permutations;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class PermutationsTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        int[] nums = new int[]{1, 2, 3};
        List<List<Integer>> actual = solution.permute(nums);
        assertEquals(normInts(List.<List<Integer>>of(List.<Integer>of(1, 2, 3), List.<Integer>of(1, 3, 2), List.<Integer>of(2, 1, 3), List.<Integer>of(2, 3, 1), List.<Integer>of(3, 1, 2), List.<Integer>of(3, 2, 1))), normInts(actual));
    }

    @Test
    void officialExample2() {
        int[] nums = new int[]{0, 1};
        List<List<Integer>> actual = solution.permute(nums);
        assertEquals(normInts(List.<List<Integer>>of(List.<Integer>of(0, 1), List.<Integer>of(1, 0))), normInts(actual));
    }

    @Test
    void officialExample3() {
        int[] nums = new int[]{1};
        List<List<Integer>> actual = solution.permute(nums);
        assertEquals(normInts(List.<List<Integer>>of(List.<Integer>of(1))), normInts(actual));
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
