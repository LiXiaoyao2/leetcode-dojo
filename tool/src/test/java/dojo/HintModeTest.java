package dojo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HintModeTest {

    @Test
    void compileErrorsDoNotStartFromClarification() {
        String log = "[ERROR] /x/Solution.java:[12,9] cannot find symbol\n[INFO] BUILD FAILURE";
        assertEquals(HintMode.COMPILE, HintMode.detect(log));
        assertFalse(HintMode.COMPILE.countsAsLevel());
    }

    @Test
    void runtimeExceptionsAreLocatedNotExplained() {
        String log = "java.lang.StackOverflowError\n\tat p0200_number_of_islands.Solution.dfs(Solution.java:18)";
        assertEquals(HintMode.DEBUG, HintMode.detect(log));
        assertEquals(18, HintMode.lineOf(log, "Solution.java"));
    }

    @Test
    void assertionFailuresUseTheCounterexample() {
        String log = "org.opentest4j.AssertionFailedError: array lengths differ, expected: <2> but was: <1>";
        assertEquals(HintMode.COUNTEREXAMPLE, HintMode.detect(log));
        assertTrue(HintMode.COUNTEREXAMPLE.countsAsLevel());
    }

    @Test
    void abbreviatedStackOverflowFromMavenStillCounts() {
        String log = "[ERROR] TwoSumTest.officialExample1:20 NullPointer Cannot invoke \"[I.clone()\" because \"actual\" is null";
        assertEquals(HintMode.DEBUG, HintMode.detect(log));
    }

    @Test
    void failuresWithoutDetailsStillCountAsCounterexample() {
        assertEquals(HintMode.COUNTEREXAMPLE,
                HintMode.detect("Tests run: 3, Failures: 3, Errors: 0, Skipped: 0"));
    }

    @Test
    void greenOrMissingLogStaysOnTheLadder() {
        assertEquals(HintMode.LADDER, HintMode.detect(null));
        assertEquals(HintMode.LADDER, HintMode.detect("Tests run: 3, Failures: 0, Errors: 0\nBUILD SUCCESS"));
    }

    @Test
    void lineOfReturnsNullWhenNoFrameMatches() {
        assertNull(HintMode.lineOf("no frames here", "Solution.java"));
    }
}
