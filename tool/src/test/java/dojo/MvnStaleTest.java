package dojo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MvnStaleTest {

    @TempDir
    Path root;

    private Path write(String rel, String content) throws IOException {
        Path file = root.resolve(rel);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void noLogMeansStale() throws IOException {
        Repo repo = Repo.at(root);
        Path code = write("solutions/src/main/java/p0001_two_sum/Solution.java", "class Solution {}\n");
        assertTrue(Mvn.stale(repo, "0001-two-sum", code));
    }

    @Test
    void logOlderThanCodeIsStale() throws IOException {
        Repo repo = Repo.at(root);
        write(".dojo/last-test.log", "problem=0001-two-sum\nclass=TwoSumTest\n---\nTests run: 3, Failures: 0, Errors: 0\n");
        Path code = write("solutions/src/main/java/p0001_two_sum/Solution.java", "class Solution {}\n");
        Files.setLastModifiedTime(root.resolve(".dojo/last-test.log"), FileTime.fromMillis(1000));
        Files.setLastModifiedTime(code, FileTime.fromMillis(2000));
        assertTrue(Mvn.stale(repo, "0001-two-sum", code));
    }

    @Test
    void freshLogForSameProblemIsNotStale() throws IOException {
        Repo repo = Repo.at(root);
        write(".dojo/last-test.log", "problem=0001-two-sum\nclass=TwoSumTest\n---\nTests run: 3, Failures: 0, Errors: 0\n");
        Path code = write("solutions/src/main/java/p0001_two_sum/Solution.java", "class Solution {}\n");
        Files.setLastModifiedTime(code, FileTime.fromMillis(1000));
        Files.setLastModifiedTime(root.resolve(".dojo/last-test.log"), FileTime.fromMillis(2000));
        assertFalse(Mvn.stale(repo, "0001-two-sum", code));
    }

    @Test
    void logForAnotherProblemIsStale() throws IOException {
        Repo repo = Repo.at(root);
        write(".dojo/last-test.log", "problem=0002-add-two-numbers\nclass=AddTwoNumbersTest\n---\nTests run: 1\n");
        Path code = write("solutions/src/main/java/p0001_two_sum/Solution.java", "class Solution {}\n");
        assertTrue(Mvn.stale(repo, "0001-two-sum", code));
    }
}
