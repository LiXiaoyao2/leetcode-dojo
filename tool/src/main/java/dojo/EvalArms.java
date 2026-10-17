package dojo;

import java.util.ArrayList;
import java.util.List;

/** 一次「某一档」的产出。 */
record EvalHint(int level, String text, long millis, int inputTokens, int outputTokens,
                boolean truncated, boolean leak, String error) {

    boolean failed() {
        return error != null || text == null || text.isBlank();
    }

    int chars() {
        return text == null ? 0 : text.strip().length();
    }
}

/** 三种架构的对照组：阶梯 / 朴素单次 / 诊断+引导。 */
final class EvalArms {

    private EvalArms() {
    }

    interface Arm {
        String name();

        String label();

        List<EvalHint> run(EvalCase evalCase, String testLog, Ai ai);
    }

    static String labelOf(String arm) {
        return switch (arm) {
            case "ladder" -> "阶梯（现在）";
            case "naive" -> "朴素单次";
            case "twopass" -> "诊断+引导";
            default -> arm;
        };
    }

    static List<Arm> all(Ai ai) {
        return List.of(new LadderArm(ai), new NaiveArm(ai), new TwoPassArm(ai));
    }

    /** 每一档交给模型的「这一次要什么」，三条 arm 完全一致，保证对照公平。 */
    static String ask(int level) {
        return switch (level) {
            case 1 -> "给出下一档提示：只澄清题意或边界（1–2 个具体问题），不透露算法方向。";
            case 2 -> "给出下一档提示：点明这题属于哪一类模式、为什么适配这组输入，不给步骤。";
            case 3 -> "给出下一档提示：给一个打破僵局的洞察，可以点名他代码里的变量或行号，不给步骤。";
            case 4 -> "给出下一档提示：3–6 步骨架 + 目标复杂度 + 一个最易错点，不要给可编译的 Java。";
            default -> "给出完整可提交的 Java 解法（```java 代码块），再附三句话讲思路、一行复杂度。";
        };
    }

    static final String COACH = """
            你是算法面试教练。学生正在 IDEA 里刷 LeetCode，卡住了才来问你。
            中文，直接给内容；不要「好的」「让我看看」这类开场白；不要复述题面；不要重复已经给过的提示。
            """;

    private static String context(EvalCase evalCase, String testLog, List<EvalHint> previous) {
        StringBuilder sb = new StringBuilder();
        sb.append("【题目】\n").append(clip(evalCase.statement(), 9000)).append("\n\n");
        sb.append("【我的代码】\n").append(clip(evalCase.stuckCode(), 6000)).append("\n\n");
        sb.append("【最近一次测试输出】\n")
                .append(testLog == null || testLog.isBlank() ? "（还没跑过）" : clip(testLog, 2500)).append("\n\n");
        sb.append("【已经给过的提示】\n");
        if (previous.isEmpty()) {
            sb.append("（无，这是第一档）\n\n");
        } else {
            for (EvalHint hint : previous) {
                sb.append("### L").append(hint.level()).append('\n')
                        .append(clip(hint.text(), 600)).append("\n\n");
            }
        }
        return sb.toString();
    }

    private static String join(String context, int level) {
        return context + "【这一次】\n" + ask(level) + "\n\n只输出这一档的正文。";
    }

    static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n…（已截断）";
    }

    private static EvalHint failure(int level, String error) {
        return new EvalHint(level, "", 0, 0, 0, false, false, error);
    }

    /** 对照组 1：现在的 dojo —— 严格阶梯、一次调用、泄底与截断自动重问。 */
    private static final class LadderArm implements Arm {
        private final Ai ai;

        LadderArm(Ai ai) {
            this.ai = ai;
        }

        @Override
        public String name() {
            return "ladder";
        }

        @Override
        public String label() {
            return "阶梯（现在）";
        }

        @Override
        public List<EvalHint> run(EvalCase evalCase, String testLog, Ai ai) {
            Ladder ladder = new Ladder(ai);
            List<Ladder.Turn> history = new ArrayList<>();
            List<EvalHint> hints = new ArrayList<>();
            for (int level = 1; level <= 5; level++) {
                Ladder.Level target = Ladder.Level.of(level);
                try {
                    Ladder.HintResult result = ladder.hint(evalCase.statement(), evalCase.stuckCode(),
                            testLog, history, target, null);
                    hints.add(new EvalHint(level, result.text(), result.millis(),
                            result.inputTokens(), result.outputTokens(),
                            result.truncated(), result.leak(), null));
                    history.add(new Ladder.Turn(level, target.name(), result.text()));
                } catch (DojoException e) {
                    hints.add(failure(level, e.getMessage()));
                    break;
                }
            }
            return hints;
        }
    }

    /** 对照组 2：朴素单次 —— 同样的输入、同样的「这一次要什么」，但没有阶梯纪律与重问。 */
    private static final class NaiveArm implements Arm {
        private final Ai ai;

        NaiveArm(Ai ai) {
            this.ai = ai;
        }

        @Override
        public String name() {
            return "naive";
        }

        @Override
        public String label() {
            return "朴素单次";
        }

        @Override
        public List<EvalHint> run(EvalCase evalCase, String testLog, Ai ai) {
            return oneShot(evalCase, testLog, ai);
        }
    }

    /** 对照组 3：诊断 + 引导 —— 旧项目那套两次调用，去掉「必须问句结尾」与兜底。 */
    private static final class TwoPassArm implements Arm {
        private final Ai ai;

        TwoPassArm(Ai ai) {
            this.ai = ai;
        }

        @Override
        public String name() {
            return "twopass";
        }

        @Override
        public String label() {
            return "诊断+引导";
        }

        @Override
        public List<EvalHint> run(EvalCase evalCase, String testLog, Ai ai) {
            List<EvalHint> hints = new ArrayList<>();
            for (int level = 1; level <= 5; level++) {
                String base = context(evalCase, testLog, hints);
                try {
                    Ai.Reply diagnosis = ai.complete("""
                            你是算法教练，只做诊断，不给建议。
                            中文直给，2–4 句：他的代码或思路最可能卡在哪、下一步最该想清楚什么。
                            """, base + "【任务】\n只输出诊断。\n", 700);
                    Ai.Reply guide = ai.complete(COACH,
                            base + "【诊断】\n" + clip(diagnosis.text(), 800) + "\n\n【这一次】\n" + ask(level)
                                    + "\n\n只输出这一档的正文。",
                            level == 5 ? 4000 : 1500);
                    if (guide.truncated()) {
                        Ai.Reply bigger = ai.complete(COACH,
                                base + "【诊断】\n" + clip(diagnosis.text(), 800) + "\n\n【这一次】\n" + ask(level)
                                        + "\n\n上一次被长度上限截断了，这次务必收尾。",
                                level == 5 ? 6000 : 2500);
                        if (!bigger.text().isBlank()) {
                            guide = bigger;
                        }
                    }
                    hints.add(new EvalHint(level, guide.text(),
                            diagnosis.millis() + guide.millis(),
                            diagnosis.inputTokens() + guide.inputTokens(),
                            diagnosis.outputTokens() + guide.outputTokens(),
                            guide.truncated(), false, null));
                } catch (DojoException e) {
                    hints.add(failure(level, e.getMessage()));
                    break;
                }
            }
            return hints;
        }
    }

    private static List<EvalHint> oneShot(EvalCase evalCase, String testLog, Ai ai) {
        List<EvalHint> hints = new ArrayList<>();
        for (int level = 1; level <= 5; level++) {
            try {
                Ai.Reply reply = ai.complete(COACH, join(context(evalCase, testLog, hints), level),
                        level == 5 ? 4000 : 1500);
                if (reply.truncated()) {
                    Ai.Reply bigger = ai.complete(COACH,
                            join(context(evalCase, testLog, hints), level) + "\n（上一次被截断了，这次务必收尾。）",
                            level == 5 ? 6000 : 2500);
                    if (!bigger.text().isBlank()) {
                        reply = bigger;
                    }
                }
                hints.add(new EvalHint(level, reply.text(), reply.millis(), reply.inputTokens(),
                        reply.outputTokens(), reply.truncated(), false, null));
            } catch (DojoException e) {
                hints.add(failure(level, e.getMessage()));
                break;
            }
        }
        return hints;
    }
}
