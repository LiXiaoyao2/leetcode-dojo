package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * dojo stress：拿一份暴力解当对照，用随机输入对拍你的解。
 * 暴力解先过官方样例才算数——不能拿一个错的对照去判你的解错。
 */
public final class Stress {

    public record Options(int cases, long seedBase, boolean refresh) {
    }

    private Stress() {
    }

    public static int run(Repo repo, String id, Options options, Ai ai) throws IOException {
        Path solution = repo.solutionFile(id);
        if (!Files.isRegularFile(solution)) {
            throw new DojoException("还没有 " + repo.root().relativize(solution) + "，先 dojo new");
        }
        Path officialTest = repo.testFile(id);
        if (!Files.isRegularFile(officialTest)) {
            throw new DojoException("没有官方样例测试（" + repo.root().relativize(officialTest)
                    + "），对拍前先让官方样例能跑");
        }
        String solutionSource = Files.readString(solution, StandardCharsets.UTF_8);
        StressGen.Spec spec;
        try {
            spec = StressGen.parse(solutionSource);
        } catch (StressGen.Unsupported e) {
            throw new DojoException(e.getMessage());
        }

        String pkg = repo.packageOf(id);
        Path mainDir = repo.solutionsDir().resolve("src/main/java").resolve(pkg);
        Path testDir = repo.solutionsDir().resolve("src/test/java").resolve(pkg);
        Path bruteFile = mainDir.resolve("Brute.java");

        if (!Files.isRegularFile(bruteFile) || options.refresh()) {
            writeBrute(repo, read(repo.problemMd(id)), spec, pkg, bruteFile, ai);
        }

        String official = Files.readString(officialTest, StandardCharsets.UTF_8);
        String base = repo.testClass(id).endsWith("Test")
                ? repo.testClass(id).substring(0, repo.testClass(id).length() - 4)
                : repo.testClass(id);
        String bruteTestClass = base + "BruteTest";
        Path bruteTest = testDir.resolve(bruteTestClass + ".java");

        if (!Files.isRegularFile(bruteTest) || options.refresh()) {
            Files.createDirectories(testDir);
            Files.writeString(bruteTest, bruteTestSource(official, repo.testClass(id), bruteTestClass),
                    StandardCharsets.UTF_8);
        }

        // 上一轮对拍留下的测试可能已经过期/编不过，先移走，免得污染这次编译
        delete(testDir.resolve(base + "StressTest.java"));

        Ui.step("先用官方样例验证暴力解…");
        Mvn.Result gate = Mvn.test(repo.root(), bruteTestClass);
        if (!gate.ok()) {
            delete(bruteTest);
            delete(bruteFile);
            Ui.err("模型给的暴力解没通过官方样例，已丢弃（留着一个错的对照只会冤枉你的代码）。");
            Ui.info("换一次：dojo stress --refresh；或者这题手写 solutions/src/main/java/" + pkg + "/Brute.java");
            System.out.println(Mvn.summarize(gate));
            return 1;
        }
        Ui.ok("暴力解通过官方样例，可以当对照。");

        String stressClass = base + "StressTest";
        Path stressFile = testDir.resolve(stressClass + ".java");
        Files.createDirectories(testDir);
        Files.writeString(stressFile,
                StressGen.testSource(pkg, stressClass, spec, options.cases(), options.seedBase()),
                StandardCharsets.UTF_8);

        Ui.step("随机对拍 " + options.cases() + " 组（种子从 " + options.seedBase() + " 开始）…");
        Mvn.Result result = Mvn.test(repo.root(), stressClass);
        Mvn.saveLastTest(repo, id, stressClass, result);
        if (result.ok()) {
            Ui.ok(options.cases() + " 组随机输入，Solution 与 Brute 结果一致。");
            Ui.info("再跑一遍同样的种子：dojo stress --seed " + options.seedBase());
            return 0;
        }
        Ui.err("对拍发现不一致——这是官方样例没覆盖到的一类输入。");
        System.out.println(Mvn.summarize(result));
        Ui.info("上面每条失败信息里的 seed 可以复现：把 dojo stress --cases 1 --seed <seed> 加到命令里。");
        Ui.info("先确认两边答案是不是都成立——这道题允许多解时，可能是假警报。");
        return 1;
    }

    /** 把官方测试改名成「用 Brute 跑同一批样例」。 */
    public static String bruteTestSource(String officialSource, String originalClass, String bruteClass) {
        String renamed = officialSource.replaceAll("(?m)^\\s*(?:public\\s+)?class\\s+" + Pattern.quote(originalClass)
                + "\\b", "class " + bruteClass);
        return renamed.replace("Solution", "Brute");
    }

    private static void writeBrute(Repo repo, String statement, StressGen.Spec spec, String pkg, Path bruteFile,
            Ai ai) throws IOException {
        Ui.step("让模型写一份暴力解（只求正确，不求快）…");
        String system = "你在给一道 LeetCode 题写「对拍用的暴力解」。只输出一个 ```java 代码块，别的什么都不说。";
        String user = "题目：\n" + clip(statement, 6000)
                + "\n\n要求：类名 Brute，包名 " + pkg + "，方法签名必须是：\n" + StressGen.signature(spec)
                + "\n正确优先：用最笨的枚举/递归都行，输入规模不超过 8。"
                + "\n不要引用 Solution；需要链表/树时用 kit.ListNode、kit.TreeNode；不要 main 方法。";
        Ai.Reply reply = ai.complete(system, user, 3000);
        String code = extractJava(reply.text());
        if (code == null) {
            throw new DojoException("模型没有给出可用的 Java 暴力解，重试：dojo stress --refresh");
        }
        Files.createDirectories(bruteFile.getParent());
        Files.writeString(bruteFile, code.endsWith("\n") ? code : code + "\n", StandardCharsets.UTF_8);
    }

    static String extractJava(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = Pattern.compile("(?s)```(?:java)?\\s*\\n(.*?)```").matcher(text);
        String best = null;
        while (m.find()) {
            String block = m.group(1).strip();
            if (block.contains("class ") || block.contains("interface ")) {
                best = block;
            }
        }
        if (best == null && Ladder.looksLikeJava(text)) {
            best = text.strip();
        }
        return best;
    }

    private static String read(Path file) throws IOException {
        return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n…（截断）";
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 尽力而为
        }
    }
}
