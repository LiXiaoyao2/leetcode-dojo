package dojo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HintFileTest {

    @TempDir
    Path root;

    @Test
    void renderCarriesLevelNoteBodyAndNextStep() {
        String md = HintFile.render("0001-two-sum", Ladder.Level.of(2), "边界处理",
                "  用哈希表把查询降到 O(1)。  ", LocalDateTime.of(2026, 10, 18, 9, 52));
        assertTrue(md.startsWith("# L2 · 方向"), md);
        assertTrue(md.contains("0001-two-sum · 2026-10-18 09:52 · 卡点：边界处理"), md);
        assertTrue(md.contains("用哈希表把查询降到 O(1)。"), md);
        assertTrue(md.contains("下一档：`dojo hint` → L3 · 洞察"), md);
    }

    @Test
    void fifthLevelHasNoNextStep() {
        String md = HintFile.render("0001-two-sum", Ladder.Level.of(5), null, "code", LocalDateTime.now());
        assertTrue(md.contains("最后一档"), md);
    }

    @Test
    void writePutsFileUnderTheProblem() throws IOException {
        Repo repo = Repo.at(root);
        Path file = HintFile.write(repo, "0001-two-sum", Ladder.Level.of(1), null, "先问边界。");
        assertEquals(root.resolve("problems/0001-two-sum/last-hint.md"), file);
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("先问边界。"));
    }
}
