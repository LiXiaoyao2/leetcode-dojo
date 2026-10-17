package dojo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** LLM 判官：逐档打分 + 四个主观旗标（泄底 / 重复 / 可行动 / 超长）。 */
final class EvalJudge {

    record Verdict(int level, int score, boolean leak, boolean repeats, boolean actionable,
                   boolean tooLong, boolean heuristic, String reason) {
    }

    static final String SYSTEM = """
            你是严格的算法教学评审。给你一道题、学生卡住的代码、更早的档位，以及待评审的这一档提示。
            评分标准（1–5）：
            5 = 恰好在那一档该有的信息量，具体、可执行、不泄底；
            4 = 有用但略泛或略超；
            3 = 正确但几乎没推进；
            2 = 方向错、重复或泄底；
            1 = 废话、误导或答非所问。
            另外逐档判断：
            leak：这一档是否出现了可直接编译的 Java 代码（L1–L4 出现即 true）；
            repeats：是否与更早的档位说了同一件事（换个说法也算）；
            actionable：学生看完能否立刻做出一个具体动作或回答一个具体问题；
            tooLong：是否明显超出该档篇幅（L1 120 / L2 150 / L3 220 / L4 350 字）。
            只输出 JSON 对象，不要任何解释、不要代码围栏：
            {"level":1,"score":4,"leak":false,"repeats":false,"actionable":true,"tooLong":false,"reason":"一句话"}
            """;

    private EvalJudge() {
    }

    static List<Verdict> judge(Ai ai, EvalCase evalCase, List<EvalHint> hints) {
        List<Verdict> verdicts = new ArrayList<>();
        for (int i = 0; i < hints.size(); i++) {
            EvalHint hint = hints.get(i);
            if (hint.failed()) {
                continue;
            }
            String user = prompt(evalCase, hints, i);
            Verdict verdict = null;
            for (int budget : new int[]{2000, 4000, 8000}) {
                Ai.Reply reply = ai.complete(SYSTEM, user
                        + (verdict == null && budget > 2000
                        ? "\n\n（上次没有给出合法 JSON——可能推理占满了长度预算。这次直接输出那一个 JSON 对象，不要思考。）"
                        : ""), budget);
                verdict = parseOne(reply.text(), hint.level());
                if (verdict != null) {
                    break;
                }
            }
            if (verdict == null) {
                verdict = heuristic(hint, hints.subList(0, i));
                System.out.println("  [judge] L" + hint.level() + " 判官没返回，已用规则回退");
            }
            verdicts.add(verdict);
        }
        return verdicts;
    }

    private static String prompt(EvalCase evalCase, List<EvalHint> hints, int index) {
        EvalHint current = hints.get(index);
        StringBuilder sb = new StringBuilder();
        sb.append("【题目】\n").append(EvalArms.clip(evalCase.statement(), 3000)).append("\n\n");
        sb.append("【学生卡住的代码】\n").append(EvalArms.clip(evalCase.stuckCode(), 2000)).append("\n\n");
        if (index > 0) {
            sb.append("【更早的档位（判断 repeats 用）】\n");
            for (int i = 0; i < index; i++) {
                if (!hints.get(i).failed()) {
                    sb.append("--- L").append(hints.get(i).level()).append(" ---\n")
                            .append(EvalArms.clip(hints.get(i).text(), 600)).append("\n\n");
                }
            }
        }
        sb.append("【待评审的这一档】L").append(current.level()).append("\n")
                .append(EvalArms.clip(current.text(), 2000)).append("\n\n");
        sb.append("只评审这一档，输出一个 JSON 对象（不要数组、不要解释）：\n")
                .append("{\"level\":").append(current.level())
                .append(",\"score\":4,\"leak\":false,\"repeats\":false,\"actionable\":true,")
                .append("\"tooLong\":false,\"reason\":\"一句话\"}");
        return sb.toString();
    }

    static Verdict parseOne(String text, int fallbackLevel) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            Map<String, Object> map = Json.obj(Json.parse(text.substring(start, end + 1)));
            if (map == null) {
                return null;
            }
            int level = map.get("level") instanceof Number n ? n.intValue() : fallbackLevel;
            int score = map.get("score") instanceof Number n ? n.intValue() : 0;
            return new Verdict(level, Math.max(0, Math.min(5, score)),
                    bool(map, "leak"), bool(map, "repeats"), bool(map, "actionable"),
                    bool(map, "tooLong"), false, map.get("reason") instanceof String s ? s : "");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 判官摆烂时的兜底：只用客观旗标推一个保守分，并标 heuristic=true。 */
    static Verdict heuristic(EvalHint hint, List<EvalHint> earlier) {
        List<String> previous = earlier.stream().map(EvalHint::text).toList();
        boolean leak = EvalMetrics.leaks(hint.text(), hint.level());
        boolean repeats = EvalMetrics.repeats(hint.text(), previous);
        boolean tooLong = EvalMetrics.tooLong(hint.text(), hint.level());
        String text = hint.text() == null ? "" : hint.text();
        boolean actionable = actionableText(text);
        int score = 3 - (leak ? 2 : 0) - (repeats ? 1 : 0) - (tooLong ? 1 : 0) + (actionable ? 1 : 0);
        score = Math.max(1, Math.min(5, score));
        return new Verdict(hint.level(), score, leak, repeats, actionable, tooLong, true,
                "（规则回退：判官未返回）");
    }

    /** L1–L4 里「能让人立刻动手」的文本特征：抛问题、列步骤、给动作。 */
    static boolean actionableText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (text.contains("？") || text.contains("?")) {
            return true;
        }
        if (Pattern.compile("(?m)^\\s*(\\d+[.、)]|[-*])\\s*\\S").matcher(text).find()) {
            return true;
        }
        return Pattern.compile("(先|试着|看看|检查|注意|考虑|确认|改成|换成|想想|回忆|比较|观察|画|列出|数一数|"
                + "避免|记住|应该|可以|需要|建议|不妨)").matcher(text).find();
    }

    private static boolean bool(Map<String, Object> map, String key) {
        return Boolean.TRUE.equals(map.get(key));
    }
}
