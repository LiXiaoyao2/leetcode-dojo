package dojo;

import java.util.regex.Pattern;

/**
 * 卡住的原因决定提示的形状。
 * 编译错误和运行时异常不该从「澄清题意」开始——那不是他的问题。
 */
public enum HintMode {

    LADDER("阶梯"),
    COMPILE("编译修复"),
    DEBUG("定位"),
    COUNTEREXAMPLE("反例");

    private static final Pattern COMPILE_FAILURE = Pattern.compile(
            "COMPILATION ERROR|Compilation failure|cannot find symbol|不兼容的类型|程序包.*不存在|"
                    + "illegal start of|';' expected|需要 ';'|缺少返回语句|找不到符号");

    private static final Pattern RUNTIME_EXCEPTION = Pattern.compile(
            "StackOverflowError|StackOverflow|NullPointerException|NullPointer|IndexOutOfBoundsException|"
                    + "IndexOutOfBounds|ArrayIndexOutOfBounds|ClassCastException|ArithmeticException|"
                    + "ConcurrentModificationException|IllegalArgumentException|NumberFormatException|"
                    + "UnsupportedOperationException|UnsupportedOperation");

    private static final Pattern ASSERTION_FAILURE = Pattern.compile(
            "AssertionFailedError|expected: <|array lengths differ|but was:|Failures: [1-9]");

    private static final Pattern ERRORS_ONLY = Pattern.compile("Errors: [1-9]");

    private final String label;

    HintMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 编译修复和定位不消耗阶梯档位：修个分号不该算你走到「洞察」。 */
    public boolean countsAsLevel() {
        return this == LADDER || this == COUNTEREXAMPLE;
    }

    public static HintMode detect(String testLog) {
        if (testLog == null || testLog.isBlank()) {
            return LADDER;
        }
        if (COMPILE_FAILURE.matcher(testLog).find()) {
            return COMPILE;
        }
        if (RUNTIME_EXCEPTION.matcher(testLog).find()) {
            return DEBUG;
        }
        if (ASSERTION_FAILURE.matcher(testLog).find()) {
            return COUNTEREXAMPLE;
        }
        if (ERRORS_ONLY.matcher(testLog).find()) {
            return DEBUG;
        }
        return LADDER;
    }

    /** 编译器给的「行:列」或栈帧里的 Solution.java:12，取第一处。 */
    public static Integer lineOf(String testLog, String fileHint) {
        if (testLog == null) {
            return null;
        }
        var m = Pattern.compile(Pattern.quote(fileHint) + ":(\\d+)").matcher(testLog);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }
}
