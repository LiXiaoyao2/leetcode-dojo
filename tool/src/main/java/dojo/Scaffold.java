package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 把一道题落成磁盘上的三样东西：题面、解法文件、官方样例测试。
 *
 * <p>原则：已有文件默认不覆盖（那是你写的代码）；测试生成失败宁可不写，
 * 写完还要编译一次，编译不过就删掉那个测试文件——绝不把一个坏文件留在
 * 模块里，让之后每道题的 dojo test 都跟着编译失败。
 */
public final class Scaffold {

    public record Outcome(String id, boolean problemWritten, boolean solutionWritten,
                          Path testFile, boolean testWritten, String note, String warning) {
    }

    private static final Pattern LEADING_COMMENT = Pattern.compile("(?s)\\A\\s*/\\*\\*.*?\\*/\\s*");
    private static final Pattern UTIL_TYPE = Pattern.compile(
            "\\b(List|Map|Set|Queue|Deque|PriorityQueue|HashMap|HashSet|ArrayList|LinkedList|"
                    + "TreeMap|TreeSet|Arrays|Collections|Stack|Iterator|Comparator|Objects)\\b");
    private static final Pattern SELF_DEFINED = Pattern.compile(
            "(?m)^\\s*(?:public\\s+)?(?:class|record|interface)\\s+(?:ListNode|TreeNode)\\b");

    private Scaffold() {
    }

    public static Outcome create(Repo repo, Problem problem, boolean force, boolean noTest,
                                 boolean noVerify, boolean unordered) {
        String id = problem.id();
        try {
            Files.createDirectories(repo.problemDir(id));
            boolean problemWritten = false;
            Path markdown = repo.problemMd(id);
            if (!Files.exists(markdown) || force) {
                Files.writeString(markdown, problem.toMarkdown() + "\n", StandardCharsets.UTF_8);
                problemWritten = true;
            }

            Path solution = repo.solutionFile(id);
            Files.createDirectories(solution.getParent());
            boolean solutionWritten = false;
            if (!Files.exists(solution) || force) {
                Files.writeString(solution, solutionSource(problem, repo.packageOf(id)), StandardCharsets.UTF_8);
                solutionWritten = true;
            }

            Path testFile = repo.testFile(id);
            String note = null;
            String warning = null;
            boolean testFresh = false;
            if (!noTest) {
                Files.createDirectories(testFile.getParent());
                if (!Files.exists(testFile) || force) {
                    TestGen.Generated generated = TestGen.generate(problem, repo.packageOf(id),
                            repo.testClass(id), unordered);
                    if (generated.source() == null) {
                        note = "没生成测试：" + generated.note();
                        Files.deleteIfExists(testFile);
                    } else {
                        Files.writeString(testFile, generated.source(), StandardCharsets.UTF_8);
                        note = generated.note();
                        testFresh = true;
                    }
                } else {
                    note = "测试文件已存在，保留你的版本";
                }
            }

            if (testFresh && !noVerify) {
                warning = verify(repo, testFile);
            }
            return new Outcome(id, problemWritten, solutionWritten,
                    Files.exists(testFile) ? testFile : null, testFresh, note, warning);
        } catch (IOException e) {
            throw new DojoException("写文件失败：" + e.getMessage());
        }
    }

    /** 写完测试立刻编译一次；测试自己编不过就删掉它，别拖累整个 solutions 模块。 */
    private static final Pattern EMPTY_BODY = Pattern.compile("\\)\\s*\\{\\s*\\}");

    /**
     * LeetCode 的 Java 模板是空方法体，直接写盘连编译都过不了——而模块里只要有一个
     * 编译不过的文件，之后每道题的 dojo test 都会跟着失败。这里给空方法体塞一句
     * TODO 的 throw：文件立刻可编译，测试跑起来是诚实的红（而不是假装通过）。
     */
    static String stubEmptyBodies(String code) {
        return EMPTY_BODY.matcher(code).replaceAll(
                ") {\n        throw new UnsupportedOperationException(\"TODO: 还没写\");\n    }");
    }

    private static String verify(Repo repo, Path testFile) {
        Mvn.Result build = Mvn.testCompile(repo.root());
        if (!build.available()) {
            return "没有编译校验（找不到 mvn）：测试文件写了，但没验证能否编译。";
        }
        if (build.exit() == 0) {
            return null;
        }
        String testErrors = errorLines(build.output(), testFile.getFileName().toString());
        if (!testErrors.isBlank()) {
            try {
                Files.deleteIfExists(testFile);
            } catch (IOException e) {
                return "生成的测试编译不过，而且删不掉（" + e.getMessage() + "）：\n" + testErrors;
            }
            return "生成的测试编译不过，已删除（避免拖垮 solutions 模块）：\n" + testErrors;
        }
        String solutionErrors = errorLines(build.output(), "Solution.java");
        if (!solutionErrors.isBlank()) {
            return "Solution.java 编译不过，dojo test 会先卡在这里（不影响其他题，但建议先让它通过编译）：\n"
                    + solutionErrors;
        }
        return "solutions 模块编译失败（不一定是这题造成的）：\n" + Mvn.tail(build.output(), 8);
    }

    private static String errorLines(String output, String fileName) {
        List<String> found = new ArrayList<>();
        for (String line : output.split("\n")) {
            String s = line.strip();
            if (s.contains(fileName) && (s.contains("[") || s.contains("error") || s.contains("错误"))) {
                found.add(s.length() > 240 ? s.substring(0, 240) + "…" : s);
            }
        }
        return String.join("\n", found.subList(0, Math.min(found.size(), 8)));
    }

    static String solutionSource(Problem problem, String pkg) {
        String template = problem.snippetJava();
        if (template == null || template.isBlank()) {
            template = "class Solution {\n\n    // 原题没有提供 Java 模板，从这里开始写。\n}\n";
        }
        template = LEADING_COMMENT.matcher(template).replaceFirst("");

        List<String> imports = new ArrayList<>();
        if (!SELF_DEFINED.matcher(template).find()) {
            if (template.contains("ListNode")) {
                imports.add("import kit.ListNode;");
            }
            if (template.contains("TreeNode")) {
                imports.add("import kit.TreeNode;");
            }
        }
        if (UTIL_TYPE.matcher(template).find()) {
            imports.add("import java.util.*;");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        for (String imp : imports) {
            sb.append(imp).append('\n');
        }
        if (!imports.isEmpty()) {
            sb.append('\n');
        }
        sb.append("// ").append(problem.id()).append("  ").append(problem.titleZh()).append('\n');
        sb.append("// ").append(problem.url()).append('\n');
        sb.append('\n');
        sb.append(stubEmptyBodies(template).stripTrailing()).append('\n');
        return sb.toString();
    }
}
