package dojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CleanTest {

    @TempDir
    Path root;

    private Repo repo() throws IOException {
        for (String marker : List.of("problems", "solutions", "tool")) {
            Files.createDirectories(root.resolve(marker));
        }
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Repo repo = Repo.at(root);
        Files.createDirectories(repo.problemDir("0001-two-sum"));
        Files.writeString(repo.problemMd("0001-two-sum"), "# 1. 两数之和");
        Files.createDirectories(repo.solutionFile("0001-two-sum").getParent());
        Files.writeString(repo.solutionFile("0001-two-sum"), "class Solution {}");
        Files.createDirectories(repo.testFile("0001-two-sum").getParent());
        Files.writeString(repo.testFile("0001-two-sum"), "class TwoSumTest {}");
        return repo;
    }

    @Test
    void pathsOfFindsProblemSourcesAndTests() throws IOException {
        Repo repo = repo();
        assertEquals(3, Clean.pathsOf(repo, "0001-two-sum").size());
    }

    @Test
    void pathsOfSkipsWhatDoesNotExist() throws IOException {
        Repo repo = repo();
        Files.delete(repo.testFile("0001-two-sum"));
        Files.delete(repo.testFile("0001-two-sum").getParent());
        assertEquals(2, Clean.pathsOf(repo, "0001-two-sum").size());
    }

    @Test
    void buildArtifactsRemovesTargetsAndLog() throws IOException {
        Repo repo = repo();
        Path target = root.resolve("tool/target");
        Files.createDirectories(target.resolve("classes/dojo"));
        Files.writeString(target.resolve("classes/dojo/Main.class"), "x".repeat(2048));
        Files.createDirectories(root.resolve("solutions/target"));
        Files.createDirectories(root.resolve(".dojo"));
        Files.writeString(root.resolve(".dojo/last-test.log"), "problem=0001-two-sum");

        Clean.Freed freed = Clean.buildArtifacts(repo);

        assertFalse(Files.exists(target));
        assertFalse(Files.exists(root.resolve("solutions/target")));
        assertFalse(Files.exists(root.resolve(".dojo/last-test.log")));
        assertEquals(3, freed.paths().size());
        assertTrue(freed.bytes() > 2048);

        Clean.Freed again = Clean.buildArtifacts(repo);
        assertTrue(again.paths().isEmpty());
        assertEquals(0, again.bytes());
    }

    @Test
    void pathsOfIsEmptyForUnknownProblem() throws IOException {
        Repo repo = repo();
        assertTrue(Clean.pathsOf(repo, "9999-nothing").isEmpty());
    }

    @Test
    void findsExecutablesOnPath() {
        assertTrue(Clean.findExecutable("sh") != null);
        assertNull(Clean.findExecutable("dojo-definitely-not-installed"));
    }

    @Test
    void humanReadsLikeAnAmount() {
        assertEquals("512 B", Clean.human(512));
        assertEquals("2.0 KB", Clean.human(2048));
        assertEquals("5.0 MB", Clean.human(5L * 1024 * 1024));
        assertEquals("1.50 GB", Clean.human(3L * 1024 * 1024 * 1024 / 2));
    }
}
