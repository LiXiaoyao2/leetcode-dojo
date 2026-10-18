package dojo;

import java.util.ArrayList;
import java.util.List;

/**
 * 行级 diff。只为一件事存在：让下一次提示看见「上次之后你改了什么」。
 * 不追求和 git 逐字节一致，够模型读懂即可。
 */
public final class Diff {

    private Diff() {
    }

    private static final int MAX_LINES = 1200;

    private record Op(char type, String text) {
    }

    /** 没有变化返回空串；代码过长返回一句说明，不抛异常。 */
    public static String unified(String before, String after, int context) {
        String oldText = before == null ? "" : before;
        String newText = after == null ? "" : after;
        if (oldText.equals(newText)) {
            return "";
        }
        String[] a = oldText.split("\n", -1);
        String[] b = newText.split("\n", -1);
        if (a.length > MAX_LINES || b.length > MAX_LINES) {
            return "（代码太长，diff 省略：上次 " + a.length + " 行，现在 " + b.length + " 行）";
        }
        List<Op> ops = diff(a, b);
        return render(ops, context);
    }

    private static List<Op> diff(String[] a, String[] b) {
        int n = a.length;
        int m = b.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<Op> ops = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                ops.add(new Op(' ', a[i]));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                ops.add(new Op('-', a[i]));
                i++;
            } else {
                ops.add(new Op('+', b[j]));
                j++;
            }
        }
        while (i < n) {
            ops.add(new Op('-', a[i++]));
        }
        while (j < m) {
            ops.add(new Op('+', b[j++]));
        }
        return ops;
    }

    private static String render(List<Op> ops, int context) {
        int pad = Math.max(0, context);
        List<int[]> ranges = new ArrayList<>();
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i).type() == ' ') {
                continue;
            }
            int start = Math.max(0, i - pad);
            int end = Math.min(ops.size() - 1, i + pad);
            if (!ranges.isEmpty() && start <= ranges.get(ranges.size() - 1)[1] + 1) {
                ranges.get(ranges.size() - 1)[1] = end;
            } else {
                ranges.add(new int[]{start, end});
            }
        }
        if (ranges.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int[] range : ranges) {
            int oldLine = 1;
            int newLine = 1;
            for (int i = 0; i < range[0]; i++) {
                Op op = ops.get(i);
                if (op.type() != '+') {
                    oldLine++;
                }
                if (op.type() != '-') {
                    newLine++;
                }
            }
            int oldCount = 0;
            int newCount = 0;
            for (int i = range[0]; i <= range[1]; i++) {
                Op op = ops.get(i);
                if (op.type() != '+') {
                    oldCount++;
                }
                if (op.type() != '-') {
                    newCount++;
                }
            }
            sb.append("@@ -").append(oldLine).append(',').append(oldCount)
                    .append(" +").append(newLine).append(',').append(newCount).append(" @@\n");
            for (int i = range[0]; i <= range[1]; i++) {
                Op op = ops.get(i);
                sb.append(op.type()).append(op.text()).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }
}
