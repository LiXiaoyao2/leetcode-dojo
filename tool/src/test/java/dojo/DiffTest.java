package dojo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffTest {

    @Test
    void identicalTextHasNoDiff() {
        assertEquals("", Diff.unified("a\nb\n", "a\nb\n", 3));
        assertEquals("", Diff.unified(null, null, 3));
    }

    @Test
    void changedLineShowsBothSides() {
        String before = "class Solution {\n    int f() { return 1; }\n}\n";
        String after = "class Solution {\n    int f() { return 2; }\n}\n";
        String diff = Diff.unified(before, after, 3);
        assertTrue(diff.contains("-    int f() { return 1; }"), diff);
        assertTrue(diff.contains("+    int f() { return 2; }"), diff);
        assertTrue(diff.startsWith("@@ -"), diff);
    }

    @Test
    void pureInsertionKeepsContext() {
        String before = "class Solution {\n}\n";
        String after = "class Solution {\n    int x = 1;\n}\n";
        String diff = Diff.unified(before, after, 3);
        assertTrue(diff.contains("+    int x = 1;"), diff);
        assertTrue(diff.contains(" class Solution {"), diff);
        assertTrue(diff.contains(" }"), diff);
    }

    @Test
    void deletionIsMarkedWithMinus() {
        String before = "a\nb\nc\n";
        String after = "a\nc\n";
        assertTrue(Diff.unified(before, after, 3).contains("-b"));
    }

    @Test
    void farApartChangesProduceTwoHunks() {
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            before.append("line").append(i).append('\n');
            after.append(i == 2 || i == 17 ? "changed" + i : "line" + i).append('\n');
        }
        String diff = Diff.unified(before.toString(), after.toString(), 1);
        assertEquals(2, diff.split("(?m)^@@ ", -1).length - 1, diff);
    }

    @Test
    void hugeFilesFallBackToSummary() {
        String big = "x\n".repeat(1201);
        String diff = Diff.unified(big, big + "y\n", 3);
        assertTrue(diff.contains("diff 省略"), diff);
    }
}
