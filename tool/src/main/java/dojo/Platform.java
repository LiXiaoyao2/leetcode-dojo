package dojo;

import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 平台差异的唯一知情者：命令名、路径形态、外部程序怎么调。
 *
 * <p>工具原本是给 macOS / Linux 写的，这里把两处最容易踩坑的差异收在一起：
 * Windows 上 Maven 是 {@code mvn.cmd}（{@code ProcessBuilder} 不会自己补后缀），
 * 以及 Maven 在 Windows 上把文件路径打印成 {@code /d:/code/...} 这种带前导斜杠的盘符形式。
 */
public final class Platform {

    private Platform() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Windows 上 mvn 是批处理脚本，必须带 .cmd 才能被 ProcessBuilder 找到。 */
    public static String mvnCommand() {
        return isWindows() ? "mvn.cmd" : "mvn";
    }

    /**
     * 把 Maven 输出里的文件路径变成可用的 Path。
     *
     * <p>Windows 上 Maven 打印 {@code /d:/code/...}，前导斜杠会让 {@link Path#of} 直接抛
     * {@link java.nio.file.InvalidPathException}（冒号非法），所以要先把它还原成 {@code d:/code/...}。
     */
    public static Path toPath(String raw) {
        String text = raw.strip();
        if (isWindows() && text.length() > 2
                && text.charAt(0) == '/'
                && Character.isLetter(text.charAt(1))
                && text.charAt(2) == ':') {
            text = text.substring(1);
        }
        return Path.of(text);
    }

    /**
     * 读外部程序（尤其是 Maven）的输出。
     *
     * <p>Maven 在中文 Windows 上按 GBK 写编译错误，而工具统一按 UTF-8 读，
     * 遇到非 UTF-8 字节会直接抛 {@link java.nio.charset.MalformedInputException}（"Input length = 1"），
     * 结果整次测试都读不到。先按 UTF-8 试，解不开再退回系统编码。
     */
    public static String readText(Path file) throws java.io.IOException {
        byte[] bytes = Files.readAllBytes(file);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(bytes, nativeCharset());
        }
    }

    /** 系统的原生编码：中文 Windows 是 GBK。 */
    public static Charset nativeCharset() {
        try {
            return Charset.forName(System.getProperty("native.encoding", "GBK"));
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    /** PATH 里找可执行文件；找不到返回 null。Windows 上同时试 .exe / .cmd / .bat。 */
    public static Path findExecutable(String name) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        // Windows 按 PATHEXT 解析命令，带扩展名的优先；无扩展名的放最后
        List<String> candidates = isWindows()
                ? List.of(name + ".exe", name + ".cmd", name + ".bat", name)
                : List.of(name);
        for (String entry : path.split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            for (String candidate : candidates) {
                Path file = Path.of(entry).resolve(candidate);
                if (Files.isRegularFile(file)) {
                    return file;
                }
            }
        }
        return null;
    }
}
