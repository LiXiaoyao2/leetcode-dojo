package p0121_best_time_to_buy_and_sell_stock;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// dojo 依据题目 metaData 与官方样例生成，可直接运行；自己的用例往下加就行。
class BestTimeToBuyAndSellStockTest {

    private final Solution solution = new Solution();

    @Test
    void officialExample1() {
        int[] prices = new int[]{7, 1, 5, 3, 6, 4};
        int actual = solution.maxProfit(prices);
        assertEquals(5, actual);
    }

    @Test
    void officialExample2() {
        int[] prices = new int[]{7, 6, 4, 3, 1};
        int actual = solution.maxProfit(prices);
        assertEquals(0, actual);
    }
}
