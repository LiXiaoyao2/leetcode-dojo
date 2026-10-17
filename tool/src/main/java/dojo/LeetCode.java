package dojo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 只读 leetcode.cn 的公开 GraphQL，不登录、不提交、不落库。
 * 题目内容抓一次就变成 problem.md，之后所有命令离线可用。
 */
public final class LeetCode {

    private static java.util.Map<String, String> NUMBER_INDEX;

    private static final String ENDPOINT = "https://leetcode.cn/graphql/";

    private static final String QUERY = """
            query questionData($s: String!) {
              question(titleSlug: $s) {
                questionFrontendId
                title
                translatedTitle
                difficulty
                content
                translatedContent
                metaData
                jsonExampleTestcases
                codeSnippets { lang langSlug code }
                topicTags { name slug translatedName }
              }
            }
            """;

    private LeetCode() {
    }

    public static Problem fetch(String slug) {
        String body = "{\"query\":" + Json.quote(QUERY) + ",\"variables\":{\"s\":" + Json.quote(slug) + "}}";
        Http.Response response = Http.postJson(ENDPOINT, body, Map.of(
                "referer", "https://leetcode.cn/problems/" + slug + "/",
                "user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36",
                "origin", "https://leetcode.cn"), Duration.ofSeconds(30));

        if (!response.ok()) {
            throw new DojoException("leetcode.cn 返回 HTTP " + response.status() + "。\n"
                    + "  · 403/429 通常是风控，等一会儿再试\n"
                    + "  · 响应片段：" + excerpt(response.body()));
        }

        Object root;
        try {
            root = Json.parse(response.body());
        } catch (IllegalArgumentException e) {
            throw new DojoException("leetcode.cn 返回的不是 JSON：" + excerpt(response.body()), e);
        }

        Object errors = Json.at(root, "errors");
        if (errors instanceof List<?> list && !list.isEmpty()) {
            throw new DojoException("GraphQL 报错：" + Json.at(list.get(0), "message"));
        }

        Object question = Json.at(root, "data", "question");
        if (question == null) {
            throw new DojoException("没有找到题目 " + slug + "。检查 slug 是否拼错（用 `dojo new <leetcode.cn 链接>` 最稳）。");
        }

        String contentHtml = firstNonBlank(
                Json.strAt(question, "translatedContent"),
                Json.strAt(question, "content"));
        String snippet = javaSnippet(question, slug);
        List<String> tags = new ArrayList<>();
        if (Json.at(question, "topicTags") instanceof List<?> tagNodes) {
            for (Object tag : tagNodes) {
                String translated = Json.strAt(tag, "translatedName");
                String name = Json.strAt(tag, "name");
                String tagName = translated == null || translated.isBlank() ? name : translated;
                if (tagName != null) {
                    tags.add(tagName);
                }
            }
        }

        return new Problem(
                Json.strAt(question, "questionFrontendId"),
                slug,
                Json.strAt(question, "translatedTitle"),
                Json.strAt(question, "title"),
                Json.strAt(question, "difficulty"),
                Html.toMarkdown(contentHtml),
                snippet,
                Json.strAt(question, "metaData"),
                Json.strAt(question, "jsonExampleTestcases"),
                tags);
    }

    /** 题号 → slug。第一次调用会把整份题目列表（约 1 MB）拉下来，之后走缓存——批量建题只花一次。 */
    public static String slugByNumber(String number) {
        String wanted = normalizeNumber(number);
        String slug = numberIndex().get(wanted);
        if (slug == null || slug.isBlank()) {
            throw new DojoException("leetcode.cn 上没有题号 " + number + "（可能是会员题，或者你记错号了）。");
        }
        return slug;
    }

    /** 全部题号索引；批量建题时反复用，所以缓存在进程里。 */
    public static java.util.Map<String, String> numberIndex() {
        if (NUMBER_INDEX == null) {
            NUMBER_INDEX = loadNumberIndex();
        }
        return NUMBER_INDEX;
    }

    private static java.util.Map<String, String> loadNumberIndex() {
        Http.Response response = Http.get("https://leetcode.cn/api/problems/all/",
                Map.of("referer", "https://leetcode.cn/problemset/", "user-agent",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36"),
                Duration.ofSeconds(30));
        if (!response.ok()) {
            throw new DojoException("拉题目列表失败：leetcode.cn 返回 " + response.status() + "。");
        }
        Object root = Json.parse(response.body());
        if (!(Json.at(root, "stat_status_pairs") instanceof List<?> pairs)) {
            throw new DojoException("leetcode.cn 的题目列表格式变了，先直接用 slug 或链接建题。");
        }
        java.util.Map<String, String> index = new java.util.HashMap<>();
        for (Object pair : pairs) {
            String id = Json.strAt(pair, "stat", "frontend_question_id");
            String slug = Json.strAt(pair, "stat", "question__title_slug");
            if (id != null && slug != null && !slug.isBlank()) {
                index.put(normalizeNumber(id), slug);
            }
        }
        return index;
    }

    static String normalizeNumber(String number) {
        return number.strip().replaceFirst("^0+(?=\\d)", "");
    }

    /**
     * 只认 Java 模板。SQL / Shell 题如果把 SQL 当模板写进 Solution.java，
     * 一个文件就让整个 solutions 模块编译不过——所有题都跑不了。
     */
    private static String javaSnippet(Object question, String slug) {
        if (!(Json.at(question, "codeSnippets") instanceof List<?> snippets)) {
            throw new DojoException(slug + " 没有提供 Java 模板，dojo 只支持 Java 题。");
        }
        List<String> languages = new ArrayList<>();
        for (Object node : snippets) {
            String langSlug = Json.strAt(node, "langSlug");
            String code = Json.strAt(node, "code");
            if (langSlug != null) {
                languages.add(langSlug);
            }
            if ("java".equals(langSlug) && code != null && !code.isBlank()) {
                return code;
            }
        }
        throw new DojoException(slug + " 只有 " + String.join(" / ", languages)
                + " 模板，不是 Java 题。dojo 只支持 Java，这道题跳过。");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String excerpt(String body) {
        if (body == null) {
            return "（空）";
        }
        String flat = body.replaceAll("\\s+", " ").strip();
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + "…";
    }
}
