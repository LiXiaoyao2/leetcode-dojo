package dojo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FadeTest {

    private static final String SOURCE = """
            package p0001_two_sum;

            class Solution {
                public int[] twoSum(int[] a, int t) {
                    Map<Integer, Integer> seen = new HashMap<>();
                    for (int i = 0; i < a.length; i++) {
                        int need = t - a[i];
                        if (seen.containsKey(need)) {
                            return new int[]{seen.get(need), i};
                        }
                        seen.put(a[i], i);
                    }
                    return new int[]{-1, -1};
                }
            }
            """;

    @TempDir
    Path root;

    @Test
    void picksLogicLinesButKeepsControlFlow() {
        List<Integer> picked = Fade.pickLines(SOURCE, 4);
        assertEquals(3, picked.size(), "两个 return 加一次 put");
        String blanked = Fade.blank(SOURCE, picked);
        assertEquals(3, blanked.split("// TODO: 补回这一行", -1).length - 1);
        assertTrue(blanked.contains("（原第 9 行）"));
        assertTrue(blanked.contains("（原第 11 行）"));
        assertTrue(blanked.contains("for (int i = 0; i < a.length; i++)"), "循环头要留着");
    }

    @Test
    void refusesWhenThereIsNoSkeletonToBlank() {
        assertTrue(Fade.pickLines("class Solution {\n    int f() {\n        return 1;\n    }\n}\n", 4).size() < 2);
    }

    @Test
    void extractsLastL5CodeBlock() throws IOException {
        Files.createDirectories(root.resolve("problems/0001-two-sum"));
        Files.writeString(root.resolve("problems/0001-two-sum/hints.md"), """
                # 提示记录 · 0001-two-sum

                ## L5 · 题解 · 2026-10-18 10:00
                ```java
                class Solution { int old() { return 1; } }
                ```

                ## L5 · 题解 · 2026-10-18 11:00
                ```java
                class Solution { int best() { return 2; } }
                ```
                """);
        String code = Fade.lastL5(Repo.at(root), "0001-two-sum");
        assertTrue(code.contains("best()"));
    }

    @Test
    void writesBackupAndRestores() throws IOException {
        Repo repo = setupRepo();
        int exit = Fade.run(repo, new String[]{"0001-two-sum", "--lines", "3"});
        assertEquals(0, exit);
        Path solution = root.resolve("solutions/src/main/java/p0001_two_sum/Solution.java");
        Path backup = root.resolve("problems/0001-two-sum/solution-full.java");
        assertTrue(Files.isRegularFile(backup));
        assertTrue(Files.readString(solution).contains("// TODO: 补回这一行"));
        assertEquals(SOURCE, Files.readString(backup));

        assertThrows(DojoException.class, () -> Fade.run(repo, new String[]{"0001-two-sum"}));

        assertEquals(0, Fade.run(repo, new String[]{"0001-two-sum", "--restore"}));
        assertEquals(SOURCE, Files.readString(solution));
        assertTrue(Files.notExists(backup));
    }

    private Repo setupRepo() throws IOException {
        Files.createDirectories(root.resolve("problems/0001-two-sum"));
        Files.createDirectories(root.resolve("solutions/src/main/java/p0001_two_sum"));
        Files.writeString(root.resolve("problems/0001-two-sum/problem.md"), "# 1. 两数之和\n");
        Files.writeString(root.resolve("problems/0001-two-sum/hints.md"), """
                ## L5 · 题解 · 2026-10-18 11:00
                ```java
                """ + SOURCE + """
                ```
                """);
        Files.writeString(root.resolve("solutions/src/main/java/p0001_two_sum/Solution.java"), SOURCE);
        return Repo.at(root);
    }
}
