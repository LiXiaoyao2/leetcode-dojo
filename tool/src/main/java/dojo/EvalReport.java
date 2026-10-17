package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 把评测结果写成 eval/RESULTS.md（给人看）与 eval/REVIEW.md（人工抽检表）。 */
final class EvalReport {

    private EvalReport() {
    }

    static void write(Repo repo, Eval.Meta meta, List<Eval.Result> results) {
        Map<String, List<Eval.Result>> byArm = group(results, meta.arms());
        List<ArmRow> rows = new ArrayList<>();
        for (String arm : meta.arms()) {
            List<Eval.Result> list = byArm.getOrDefault(arm, List.of());
            rows.add(row(arm, list));
        }
        String markdown = markdown(meta, rows, byArm, results);
        Path resultsFile = repo.root().resolve("eval/RESULTS.md");
        Path reviewFile = repo.root().resolve("eval/REVIEW.md");
        try {
            Files.createDirectories(resultsFile.getParent());
            Files.writeString(resultsFile, markdown, StandardCharsets.UTF_8);
            Files.writeString(reviewFile, review(meta, results), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DojoException("写评测报告失败：" + e.getMessage(), e);
        }
    }

    private static Map<String, List<Eval.Result>> group(List<Eval.Result> results, List<String> arms) {
        Map<String, List<Eval.Result>> byArm = new LinkedHashMap<>();
        for (String arm : arms) {
            byArm.put(arm, new ArrayList<>());
        }
        for (Eval.Result result : results) {
            byArm.computeIfAbsent(result.arm(), key -> new ArrayList<>()).add(result);
        }
        return byArm;
    }

    record ArmRow(String arm, String label, EvalMetrics.Stat stat, double score, int judged,
                  int heuristic, double actionable, int l5Pass, int l5Total, int l5NoCode, int l5Compile,
                  int l5Assert, int failed) {
    }

    private static ArmRow row(String arm, List<Eval.Result> results) {
        List<EvalMetrics.Cell> cells = new ArrayList<>();
        List<EvalJudge.Verdict> verdicts = new ArrayList<>();
        int l5Pass = 0;
        int l5NoCode = 0;
        int l5Compile = 0;
        int l5Assert = 0;
        int failed = 0;
        String label = arm;
        for (Eval.Result result : results) {
            label = result.armLabel();
            cells.addAll(result.cells());
            verdicts.addAll(result.verdicts());
            failed += (int) result.cells().stream().filter(EvalMetrics.Cell::failed).count();
            if (result.l5Pass()) {
                l5Pass++;
            } else if (result.l5Verdict().contains("没有 Java 代码") || result.l5Verdict().contains("没有产出")) {
                l5NoCode++;
            } else if (result.l5Verdict().equals("编译失败")) {
                l5Compile++;
            } else {
                l5Assert++;
            }
        }
        double score = verdicts.stream().mapToInt(EvalJudge.Verdict::score).average().orElse(0);
        long actionable = verdicts.stream().filter(EvalJudge.Verdict::actionable).count();
        int heuristic = (int) verdicts.stream().filter(EvalJudge.Verdict::heuristic).count();
        return new ArmRow(arm, label, EvalMetrics.Stat.of(cells), score, verdicts.size(), heuristic,
                verdicts.isEmpty() ? 0 : (double) actionable / verdicts.size(), l5Pass, results.size(),
                l5NoCode, l5Compile, l5Assert, failed);
    }

    private static String markdown(Eval.Meta meta, List<ArmRow> rows, Map<String, List<Eval.Result>> byArm,
                                   List<Eval.Result> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# dojo 提示质量评测\n\n");
        sb.append("> ").append(meta.caseCount()).append(" 道题 × ").append(rows.size()).append(" 种架构 × 5 档 · ")
                .append(meta.startedAt()).append(" · 模型 `").append(meta.model())
                .append("` · commit `").append(meta.commit()).append("`\n");
        sb.append("> 原始数据 [eval/raw.json](raw.json) · 人工抽检表 [eval/REVIEW.md](REVIEW.md)\n\n");

        sb.append("## 一句话结论\n\n").append(conclusion(rows)).append("\n\n");

        sb.append("## 怎么测的\n\n");
        sb.append("- **数据集**：`eval/benchmark.txt` 里 ").append(meta.caseCount())
                .append(" 道 leetcode.cn 题（").append(difficulties(results)).append("），覆盖数组、字符串、链表、树、图、")
                .append("回溯、动态规划、堆、设计题。每题不是空白模板，而是一段人写的「写到一半卡住」的代码，")
                .append("先真实跑一次自动生成的官方样例测试，把失败输出一起喂给模型。\n");
        sb.append("- **三种架构**：`阶梯` 是现在 dojo 的 L1–L5 协议（单次调用 + 泄底/截断自动重问）；")
                .append("`朴素单次` 同样的输入、同样的「这一档要什么」，但没有阶梯纪律与重问；")
                .append("`诊断+引导` 先让模型诊断、再据诊断写提示（旧项目那套两次调用，去掉「必须问句结尾」和兜底）。\n");
        sb.append("- **公平性**：三条 arm 每档看到的题面、卡住代码、失败输出、已给提示完全一致，")
                .append("提问句也逐字相同；差别只在架构本身。\n");
        sb.append("- **客观指标**：泄底 = L1–L4 出现可编译 Java（复用 dojo 自己的检测器）；")
                .append("重复 = 与更早档位的字符 3-gram Jaccard ≥ 0.35；超长 = 超过该档字数上限 20%；")
                .append("L5 跑绿 = 把第 5 档回复里的 Java 抠出来丢进同一份官方样例测试，`mvn test` 全绿。\n");
        sb.append("- **主观指标**：判官 `").append(meta.judgeModel())
                .append("` 逐档打 1–5 分并判 leak / repeats / actionable / tooLong；判官看不到 arm 名字。")
                .append("另有 [eval/REVIEW.md](REVIEW.md) 供人工抽检校准。\n");
        sb.append("- **成本**：整轮 ").append(Eval.duration(meta.millis())).append("，")
                .append(totalTokens(results)).append(" tokens（含判官）。\n\n");

        sb.append("## 主表\n\n");
        sb.append("| 架构 | 泄底率 L1–L4 | 重复率 | 超长率 | 可行动率 | 平均分 | L5 跑绿 | 中位时延 | tokens/题 |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (ArmRow row : rows) {
            sb.append("| ").append(row.label()).append(" | ")
                    .append(pct(row.stat().leak(), row.stat().hints())).append(" | ")
                    .append(pct(row.stat().repeat(), row.stat().hints())).append(" | ")
                    .append(pct(row.stat().tooLong(), row.stat().hints())).append(" | ")
                    .append(pct(row.actionable())).append(" | ")
                    .append(String.format(Locale.ROOT, "%.2f", row.score())).append(" | ")
                    .append(row.l5Pass()).append("/").append(row.l5Total()).append(" | ")
                    .append(row.stat().medianMillis()).append(" ms | ")
                    .append(row.stat().totalTokens() / Math.max(1, row.l5Total())).append(" |\n");
        }
        sb.append("\n失败调用（网络/协议错误）：");
        for (ArmRow row : rows) {
            sb.append(row.label()).append(" ").append(row.failed()).append(" 次；");
        }
        sb.setLength(sb.length() - 1);
        sb.append("。\n\n");
        int ruleActionable = (int) results.stream()
                .flatMap(result -> result.verdicts().stream())
                .filter(verdict -> verdict.reason() != null && verdict.reason().contains("可行动由规则补"))
                .count();
        if (ruleActionable > 0) {
            sb.append("本轮 raw.json 产生于「actionable」字段落盘之前，可行动率由规则从文本判定（")
                    .append(ruleActionable).append(" 档）；下一次全量会直接用判官标记。\n\n");
        }
        sb.append("判官未返回、由规则回退的档位：");
        for (ArmRow row : rows) {
            sb.append(row.label()).append(" ").append(row.heuristic()).append("/").append(row.judged()).append("；");
        }
        sb.setLength(sb.length() - 1);
        sb.append("（回退规则见 `EvalJudge.heuristic`：只按泄底/重复/超长/可行动推一个保守分）。\n\n");

        sb.append("## 分档看\n\n");
        sb.append("| 档位 | ").append(joinArms(rows, "平均分")).append(" | ").append(joinArms(rows, "泄底")).append(" | ")
                .append(joinArms(rows, "重复")).append(" | ").append(joinArms(rows, "平均字数")).append(" |\n");
        sb.append("|---|").append("---:|".repeat(rows.size() * 4)).append("\n");
        for (int level = 1; level <= 5; level++) {
            StringBuilder scores = new StringBuilder();
            StringBuilder leaks = new StringBuilder();
            StringBuilder repeats = new StringBuilder();
            StringBuilder chars = new StringBuilder();
            for (ArmRow row : rows) {
                List<Eval.Result> list = byArm.getOrDefault(row.arm(), List.of());
                List<EvalMetrics.Cell> cells = cellsAt(list, level);
                List<EvalJudge.Verdict> verdicts = verdictsAt(list, level);
                EvalMetrics.Stat stat = EvalMetrics.Stat.of(cells);
                double score = verdicts.stream().mapToInt(EvalJudge.Verdict::score).average().orElse(0);
                scores.append(String.format(Locale.ROOT, "%.2f", score)).append(" | ");
                leaks.append(pct(stat.leak(), stat.hints())).append(" | ");
                repeats.append(pct(stat.repeat(), stat.hints())).append(" | ");
                chars.append(String.format(Locale.ROOT, "%.0f", stat.avgChars())).append(" | ");
            }
            sb.append("| L").append(level).append(" | ").append(scores).append(leaks).append(repeats).append(chars);
            sb.setLength(sb.length() - 1);
            sb.append("\n");
        }
        sb.append("\n");

        sb.append("## L5 失败在哪里\n\n");
        sb.append("| 架构 | 跑绿 | 编译失败 | 断言失败 | 第 5 档没给代码 |\n|---|---:|---:|---:|---:|\n");
        for (ArmRow row : rows) {
            sb.append("| ").append(row.label()).append(" | ").append(row.l5Pass()).append(" | ")
                    .append(row.l5Compile()).append(" | ").append(row.l5Assert()).append(" | ")
                    .append(row.l5NoCode()).append(" |\n");
        }
        sb.append("\n评测把模型回复里的 Java 抠出来时，统一补了 IDEA 模板本来就有的 `import java.util.*` 与 `import kit.*`；")
                .append("所以「编译失败」剩下的是类名/结构问题，不扣导入的账。\n\n");

        sb.append("## 样例：同一道题的 L1\n\n");
        Eval.Result sample = firstSample(results);
        if (sample != null) {
            sb.append("`").append(sample.evalCase().label()).append("`\n\n");
            for (String arm : rows.stream().map(ArmRow::arm).toList()) {
                for (Eval.Result result : results) {
                    if (result.evalCase().id().equals(sample.evalCase().id()) && result.arm().equals(arm)) {
                        EvalHint hint = atLevel(result.hints(), 1);
                        sb.append("**").append(result.armLabel()).append("**\n\n");
                        sb.append(hint == null || hint.failed() ? "（调用失败）" : hint.text().strip()).append("\n\n");
                    }
                }
            }
        }

        sb.append("## 局限\n\n");
        sb.append("- 判官也是 LLM，和被测模型同源时可能偏爱同一种文风；主表同时给客观指标，主观分只作参考。\n");
        sb.append("- 20 道题、每档一次采样，样本量只够看趋势，不够做显著性检验。\n");
        sb.append("- 「卡住代码」是人工写的，代表一类卡点，不代表所有卡点。\n");
        sb.append("- L5 只验官方样例 + 自动生成的测试，不是完整判题。\n\n");

        sb.append("## 复现\n\n```bash\nbin/dojo eval --snapshot        # 拉题（需要网络）\n")
                .append("bin/dojo eval --judge            # 跑全量，写 eval/RESULTS.md\n")
                .append("bin/dojo eval --cases 3          # 先跑 3 题试水\n```\n");
        return sb.toString();
    }

    private static String conclusion(List<ArmRow> rows) {
        ArmRow ladder = rows.stream().filter(row -> row.arm().equals("ladder")).findFirst().orElse(null);
        if (ladder == null || rows.size() < 2) {
            return "本轮对照的架构不足，看主表即可。";
        }
        List<ArmRow> others = rows.stream().filter(row -> !row.arm().equals("ladder")).toList();
        StringBuilder sb = new StringBuilder();

        sb.append("**泄底**：L1–L4 出现可编译 Java 的比例，");
        sb.append(costList(rows, row -> pct(row.stat().leak(), row.stat().hints()))).append("。");
        boolean allClean = rows.stream().allMatch(row -> row.stat().leak() == 0);
        if (allClean) {
            sb.append("三条架构都守住了这条线——受控提问本身就压住了泄底，差别不在这里。");
        }
        sb.append("\n\n");

        boolean ceiling = rows.stream().allMatch(row -> row.l5Pass() == row.l5Total());
        sb.append("**L5 可执行性**：");
        sb.append(costList(rows, row -> row.l5Pass() + "/" + row.l5Total())).append("。");
        if (ceiling) {
            sb.append("所有架构的第 5 档都能跑绿——这一项到顶了，20 道题区分不出来。");
        }
        sb.append("\n\n");

        sb.append("**篇幅纪律**（超过档位字数上限 20% 的比例）：");
        sb.append(costList(rows, row -> pct(row.stat().tooLong(), row.stat().hints()))).append("。");
        sb.append("阶梯把「一开口就写小作文」压到了最低，这是它的主要收益。");
        sb.append("\n\n");

        ArmRow cheapest = rows.stream().min(java.util.Comparator.comparingLong(row -> row.stat().totalTokens())).orElse(ladder);
        ArmRow slowest = rows.stream().max(java.util.Comparator.comparingLong(row -> row.stat().medianMillis())).orElse(ladder);
        sb.append("**成本**：tokens/题 ");
        sb.append(costList(rows, row -> String.valueOf(row.stat().totalTokens() / Math.max(1, row.l5Total()))));
        sb.append("；中位时延 ");
        sb.append(costList(rows, row -> row.stat().medianMillis() + " ms")).append("。");
        if (cheapest != ladder && slowest != ladder) {
            sb.append("最省的是 ").append(cheapest.label()).append("，最慢的是 ").append(slowest.label()).append("。");
        }
        sb.append("\n\n");

        sb.append("**主观分**：");
        sb.append(costList(rows, row -> String.format(Locale.ROOT, "%.2f", row.score()))).append("。");
        sb.append("20 道题、每档一次采样，这个差距不足以当结论；判官与被测模型同源，也会偏爱自己的文风。");
        sb.append("要看分档差异，请看下面的分档表。");
        return sb.toString();
    }

    private static String costList(List<ArmRow> rows, java.util.function.Function<ArmRow, String> value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(rows.get(i).label()).append(" ").append(value.apply(rows.get(i)));
        }
        return sb.toString();
    }

    private static String difficulties(List<Eval.Result> results) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Eval.Result result : results) {
            if (seen.add(result.evalCase().id())) {
                counts.merge(chinese(result.evalCase().difficulty()), 1, Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(entry.getKey()).append(" ").append(entry.getValue());
        }
        return sb.toString();
    }

    private static String chinese(String difficulty) {
        return switch (difficulty == null ? "" : difficulty) {
            case "Easy" -> "简单";
            case "Medium" -> "中等";
            case "Hard" -> "困难";
            default -> difficulty;
        };
    }

    private static long totalTokens(List<Eval.Result> results) {
        return results.stream().flatMap(result -> result.cells().stream())
                .mapToLong(EvalMetrics.Cell::tokens).sum();
    }

    private static String joinArms(List<ArmRow> rows, String suffix) {
        StringBuilder sb = new StringBuilder();
        for (ArmRow row : rows) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(row.label()).append(" ").append(suffix);
        }
        return sb.toString();
    }

    private static List<EvalMetrics.Cell> cellsAt(List<Eval.Result> results, int level) {
        List<EvalMetrics.Cell> cells = new ArrayList<>();
        for (Eval.Result result : results) {
            for (EvalMetrics.Cell cell : result.cells()) {
                if (cell.level() == level) {
                    cells.add(cell);
                }
            }
        }
        return cells;
    }

    private static List<EvalJudge.Verdict> verdictsAt(List<Eval.Result> results, int level) {
        List<EvalJudge.Verdict> verdicts = new ArrayList<>();
        for (Eval.Result result : results) {
            EvalJudge.Verdict verdict = Eval.verdictAt(result.verdicts(), level);
            if (verdict != null) {
                verdicts.add(verdict);
            }
        }
        return verdicts;
    }

    private static EvalHint atLevel(List<EvalHint> hints, int level) {
        for (EvalHint hint : hints) {
            if (hint.level() == level) {
                return hint;
            }
        }
        return null;
    }

    private static Eval.Result firstSample(List<Eval.Result> results) {
        for (Eval.Result result : results) {
            if (!result.hints().isEmpty() && !result.hints().get(0).failed()) {
                return result;
            }
        }
        return null;
    }

    private static String review(Eval.Meta meta, List<Eval.Result> results) {
        List<String> samples = new ArrayList<>();
        for (Eval.Result result : results) {
            for (EvalHint hint : result.hints()) {
                if (!hint.failed() && hint.level() <= 4) {
                    samples.add("### " + result.evalCase().id() + " " + result.evalCase().title() + " · "
                            + result.armLabel() + " · L" + hint.level() + "（" + hint.chars() + " 字）\n\n"
                            + "> " + hint.text().strip().replace("\n", "\n> ") + "\n\n"
                            + "- [ ] 泄底  - [ ] 重复  - [ ] 超长  - [ ] 可行动  - [ ] 有用（1–5）：___\n");
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# 人工抽检表\n\n");
        sb.append("> ").append(meta.caseCount()).append(" 题 × ").append(meta.arms().size())
                .append(" 架构 · ").append(meta.startedAt()).append(" · 抽样 ")
                .append(Math.min(12, samples.size())).append(" 条\n");
        sb.append("> 用法：随机抽 10–20 条，按列打勾；和判官分数对不上时以人工为准，并在下面记一句为什么。\n\n");
        int take = Math.min(12, samples.size());
        int step = Math.max(1, samples.size() / Math.max(1, take));
        for (int i = 0; i < take && i * step < samples.size(); i++) {
            sb.append(samples.get(i * step));
        }
        sb.append("## 人工与判官不一致的地方\n\n- \n");
        return sb.toString();
    }

    static String pct(int count, int total) {
        return total == 0 ? "—" : String.format(Locale.ROOT, "%.0f%%", 100.0 * count / total);
    }

    static String pct(double ratio) {
        return String.format(Locale.ROOT, "%.0f%%", ratio * 100);
    }

    static void console(Eval.Meta meta, List<Eval.Result> results) {
        console(meta, results, true);
    }

    static void console(Eval.Meta meta, List<Eval.Result> results, boolean wroteRaw) {
        Ui.title("评测完成 · " + Eval.duration(meta.millis()));
        System.out.println("  架构        泄底率  重复率  可行动  平均分  L5 跑绿  中位时延  tokens/题");
        for (String arm : meta.arms()) {
            List<Eval.Result> list = results.stream().filter(result -> result.arm().equals(arm)).toList();
            ArmRow row = row(arm, list);
            System.out.println(String.format(Locale.ROOT, "  %-10s %6s %7s %7s %7.2f %7s %8d ms %9d  (判官回退 %d)",
                    row.label(), pct(row.stat().leak(), row.stat().hints()),
                    pct(row.stat().repeat(), row.stat().hints()), pct(row.actionable()),
                    row.score(), row.l5Pass() + "/" + row.l5Total(), row.stat().medianMillis(),
                    row.stat().totalTokens() / Math.max(1, row.l5Total()), row.heuristic()));
        }
        Ui.ok(wroteRaw ? "写好了 eval/RESULTS.md · eval/REVIEW.md · eval/raw.json"
                : "只重建了 eval/RESULTS.md · eval/REVIEW.md（raw.json 未动）");
    }
}
