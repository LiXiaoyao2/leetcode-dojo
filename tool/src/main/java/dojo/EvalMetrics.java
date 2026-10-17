package dojo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 客观指标：泄底、重复、超长、篇幅、时延。判官的主观评分在 EvalJudge。 */
final class EvalMetrics {

    static final int[] LIMITS = {0, 120, 150, 220, 350, Integer.MAX_VALUE};
    static final double REPEAT_THRESHOLD = 0.35;

    private EvalMetrics() {
    }

    /** L1–L4 出现可编译 Java 就算泄底（复用教练自己的检查器，标准一致）。 */
    static boolean leaks(String text, int level) {
        if (level >= 5 || text == null) {
            return false;
        }
        return Ladder.looksLikeJava(text);
    }

    static boolean tooLong(String text, int level) {
        if (level < 1 || level > 4 || text == null) {
            return false;
        }
        return text.strip().length() > LIMITS[level] * 1.2;
    }

    /** 和之前任意一档的字符 3-gram Jaccard 相似度是否越线。 */
    static boolean repeats(String text, List<String> previous) {
        for (String earlier : previous) {
            if (similarity(text, earlier) >= REPEAT_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    static double similarity(String a, String b) {
        Set<String> left = grams(a);
        Set<String> right = grams(b);
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        int both = 0;
        for (String gram : left) {
            if (right.contains(gram)) {
                both++;
            }
        }
        return (double) both / union.size();
    }

    private static Set<String> grams(String text) {
        String flat = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        Set<String> set = new HashSet<>();
        for (int i = 0; i + 3 <= flat.length(); i++) {
            set.add(flat.substring(i, i + 3));
        }
        return set;
    }

    /** 逐档算出来的客观数据（不含判官）。 */
    static List<Cell> of(List<EvalHint> hints) {
        List<Cell> cells = new ArrayList<>();
        List<String> previous = new ArrayList<>();
        for (EvalHint hint : hints) {
            boolean leak = leaks(hint.text(), hint.level());
            boolean repeat = repeats(hint.text(), previous);
            boolean longEnough = tooLong(hint.text(), hint.level());
            cells.add(new Cell(hint.level(), hint.text(), hint.chars(), leak, repeat, longEnough,
                    hint.millis(), hint.inputTokens() + hint.outputTokens(), hint.failed(), hint.truncated()));
            previous.add(hint.text());
        }
        return cells;
    }

    record Cell(int level, String text, int chars, boolean leak, boolean repeat, boolean tooLong,
                long millis, int tokens, boolean failed, boolean truncated) {
    }

    /** 一档或整轮的汇总。 */
    record Stat(int hints, int failed, int leak, int repeat, int tooLong, double avgChars,
                long medianMillis, int totalTokens, int truncated) {

        double rate(int count) {
            return hints == 0 ? 0 : (double) count / hints;
        }

        static Stat of(List<Cell> cells) {
            List<Cell> ok = cells.stream().filter(c -> !c.failed()).toList();
            int leak = (int) ok.stream().filter(Cell::leak).count();
            int repeat = (int) ok.stream().filter(Cell::repeat).count();
            int tooLong = (int) ok.stream().filter(Cell::tooLong).count();
            int truncated = (int) ok.stream().filter(Cell::truncated).count();
            double chars = ok.stream().mapToInt(Cell::chars).average().orElse(0);
            List<Long> times = ok.stream().map(Cell::millis).sorted().toList();
            long median = times.isEmpty() ? 0 : times.get(times.size() / 2);
            int tokens = ok.stream().mapToInt(Cell::tokens).sum();
            return new Stat(cells.size(), cells.size() - ok.size(), leak, repeat, tooLong,
                    round(chars), median, tokens, truncated);
        }
    }

    static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
