package dojo;

/** 控制台输出的唯一出口：统一前缀，方便 grep，也方便以后换颜色。 */
public final class Ui {

    private Ui() {
    }

    public static void line() {
        System.out.println();
    }

    public static void info(String message) {
        System.out.println(message);
    }

    public static void step(String message) {
        System.out.println("→ " + message);
    }

    public static void ok(String message) {
        System.out.println("✓ " + message);
    }

    public static void warn(String message) {
        System.out.println("! " + message);
    }

    public static void err(String message) {
        System.err.println("✗ " + message);
    }

    public static void title(String message) {
        System.out.println();
        System.out.println(message);
        System.out.println("─".repeat(Math.min(60, Math.max(8, message.codePointCount(0, message.length())))));
    }
}
