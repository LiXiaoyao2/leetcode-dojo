package dojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvalJudgeTest {

    @Test
    void parsesPlainJsonObject() {
        EvalJudge.Verdict verdict = EvalJudge.parseOne(
                "{\"level\":3,\"score\":4,\"leak\":false,\"repeats\":true,"
                        + "\"actionable\":true,\"tooLong\":false,\"reason\":\"有用\"}", 3);
        assertEquals(3, verdict.level());
        assertEquals(4, verdict.score());
        assertTrue(verdict.repeats());
        assertTrue(verdict.actionable());
        assertFalse(verdict.heuristic());
        assertEquals("有用", verdict.reason());
    }

    @Test
    void parsesObjectWrappedInProseAndFence() {
        EvalJudge.Verdict verdict = EvalJudge.parseOne(
                "评审如下：\n```json\n{\"level\":5,\"score\":5,\"leak\":false}\n```\n以上。", 5);
        assertEquals(5, verdict.level());
        assertEquals(5, verdict.score());
    }

    @Test
    void clampsOutOfRangeScore() {
        assertEquals(5, EvalJudge.parseOne("{\"level\":1,\"score\":9}", 1).score());
        assertEquals(0, EvalJudge.parseOne("{\"level\":1,\"score\":-3}", 1).score());
    }

    @Test
    void returnsNullForNonJson() {
        assertNull(EvalJudge.parseOne("好的，我来评审一下。", 2));
        assertNull(EvalJudge.parseOne("", 2));
    }

    @Test
    void heuristicMarksItselfAndPunishesLeak() {
        EvalHint leak = new EvalHint(3, "class Solution { public int f() { return 1; } }", 0, 0, 0, false, false, null);
        EvalJudge.Verdict verdict = EvalJudge.heuristic(leak, List.of());
        assertTrue(verdict.heuristic());
        assertTrue(verdict.leak());
        assertTrue(verdict.score() <= 2);
    }

    @Test
    void heuristicRewardsAQuestionAtLevelOne() {
        EvalHint question = new EvalHint(1, "你确认 i == j 这次迭代合法吗？", 0, 0, 0, false, false, null);
        EvalJudge.Verdict verdict = EvalJudge.heuristic(question, List.of());
        assertTrue(verdict.actionable());
        assertFalse(verdict.leak());
    }
}
