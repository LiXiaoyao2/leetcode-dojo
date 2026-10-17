package dojo;

import java.util.List;

/** 一道题的完整快照：抓取时一次性拿到，落盘成 problem.md 后其余命令只读盘。 */
public record Problem(
        String frontendId,
        String slug,
        String titleZh,
        String titleEn,
        String difficulty,
        String contentMd,
        String snippetJava,
        String metaDataJson,
        String examplesJson,
        List<String> tags) {

    public String id() {
        return Naming.id(frontendId, slug);
    }

    public String title() {
        String title = titleZh == null || titleZh.isBlank() ? titleEn : titleZh;
        return (frontendId == null ? "" : frontendId + ". ") + (title == null ? slug : title);
    }

    /** LeetCode 返回的是 Easy/Medium/Hard，落盘和展示统一用中文。 */
    public String difficultyZh() {
        if (difficulty == null || difficulty.isBlank()) {
            return "未知";
        }
        return switch (difficulty) {
            case "Easy" -> "简单";
            case "Medium" -> "中等";
            case "Hard" -> "困难";
            default -> difficulty;
        };
    }

    public String url() {
        return "https://leetcode.cn/problems/" + slug + "/";
    }

    /** 落盘的 problem.md：人读和模型读的是同一份。 */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title()).append("\n\n");
        String tagLine = tags == null || tags.isEmpty() ? "" : " · 标签：" + String.join(" / ", tags);
        sb.append("> 难度：").append(difficultyZh()).append(tagLine).append('\n');
        sb.append("> ").append(url()).append("\n\n");
        sb.append(contentMd == null ? "" : contentMd.strip()).append('\n');
        return sb.toString();
    }
}
