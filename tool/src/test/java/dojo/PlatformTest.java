package dojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformTest {

    @Test
    void mvnCommandCarriesTheWindowsSuffix() {
        assertEquals(Platform.isWindows() ? "mvn.cmd" : "mvn", Platform.mvnCommand());
    }

    @Test
    void toPathStripsTheLeadingSlashBeforeADriveLetter() {
        // Maven 在 Windows 上打印 /d:/code/...，Path.of 直接吃会抛 InvalidPathException
        Path path = Platform.toPath("/d:/code/repo/solutions/src/main/java/p0001_x/Solution.java");
        assertTrue(path.toString().contains("code"));
        assertEquals("Solution.java", path.getFileName().toString());
    }

    @Test
    void toPathKeepsUnixPathsAsIs() {
        Path path = Platform.toPath("/home/u/repo/solutions/src/main/java/p0001_x/Solution.java");
        assertEquals("Solution.java", path.getFileName().toString());
    }

    @Test
    void javaFilesInFindsMavenErrorPaths() {
        String output = """
                [ERROR] /home/u/repo/solutions/src/main/java/p0001_x/Solution.java:[3,5] 错误: 需要 ';'
                [ERROR] /home/u/repo/solutions/src/test/java/p0001_x/TwoSumTest.java:[7,1] 错误: 找不到符号
                """;
        List<Path> files = Mvn.javaFilesIn(output);
        assertEquals(2, files.size());
        assertEquals("Solution.java", files.get(0).getFileName().toString());
        assertEquals("TwoSumTest.java", files.get(1).getFileName().toString());
    }

    @Test
    void javaFilesInHandlesWindowsDriveLetters() {
        String output = "[ERROR] /d:/code/repo/solutions/src/main/java/p0001_x/Solution.java:[3,5] error";
        List<Path> files = Mvn.javaFilesIn(output);
        assertEquals(1, files.size());
        assertEquals("Solution.java", files.get(0).getFileName().toString());
    }
}
