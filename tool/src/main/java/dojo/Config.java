package dojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关配置的解析与合并。
 *
 * <p>优先级（先找到 base_url 的来源胜出）：DOJO_* 环境变量 → 仓库 {@code .dojo/config}
 * → {@code ~/.config/dojo/config} → {@code ~/.claude/settings.json}。后者的作用只是
 * 「本机已有可用网关，别再让我配一遍」；dojo 不会把它里面的 key 写进仓库。
 */
public final class Config {

    /** 一个已经合并好的网关配置。 */
    public record Gateway(String baseUrl, String token, String model, String provider, String source) {

        public String maskedToken() {
            if (token == null || token.isBlank()) {
                return "（缺失）";
            }
            if (token.length() <= 4) {
                return "****（" + token.length() + " 位）";
            }
            return "…" + token.substring(token.length() - 4) + "（" + token.length() + " 位）";
        }

        public String endpoint() {
            String base = baseUrl.replaceAll("/+$", "");
            if ("openai".equals(provider)) {
                if (base.endsWith("/chat/completions")) {
                    return base;
                }
                return base.endsWith("/v1") ? base + "/chat/completions" : base + "/v1/chat/completions";
            }
            if (base.endsWith("/messages")) {
                return base;
            }
            return base.endsWith("/v1") ? base + "/messages" : base + "/v1/messages";
        }
    }

    private Config() {
    }

    public static Gateway load(Repo repo) {
        List<Gateway> sources = new ArrayList<>();

        Map<String, String> env = new LinkedHashMap<>();
        put(env, "base_url", System.getenv("DOJO_BASE_URL"));
        String envKey = firstNonBlank(System.getenv("DOJO_API_KEY"), System.getenv("DOJO_AUTH_TOKEN"));
        put(env, "api_key", envKey);
        put(env, "model", System.getenv("DOJO_MODEL"));
        put(env, "provider", System.getenv("DOJO_PROVIDER"));
        sources.add(fromMap(env, "环境变量 DOJO_*"));

        sources.add(fromFile(repo.root().resolve(".dojo/config"), ".dojo/config"));
        sources.add(fromFile(homeConfig(), "~/.config/dojo/config"));
        sources.add(fromClaudeSettings(Path.of(System.getProperty("user.home"), ".claude", "settings.json")));

        Gateway chosen = sources.stream()
                .filter(g -> g.baseUrl() != null && !g.baseUrl().isBlank())
                .findFirst()
                .orElseThrow(() -> new DojoException("""
                        没有找到模型网关配置。任选一种方式：
                          · 在仓库里写 .dojo/config：
                              base_url=https://你的网关
                              api_key=你的 key
                              model=deepseek-v4.1-flash
                              provider=anthropic        # 或 openai
                          · 或设置环境变量 DOJO_BASE_URL / DOJO_API_KEY / DOJO_MODEL
                          · 或让 ~/.claude/settings.json 里的 ANTHROPIC_BASE_URL 可用
                        写好后运行 `dojo doctor` 自检。"""));

        String model = firstNonBlank(chosen.model(), firstModel(sources), "deepseek-v4.1-flash");
        String provider = firstNonBlank(chosen.provider(), inferProvider(chosen.baseUrl()), "anthropic");
        String token = chosen.token();
        if (token == null || token.isBlank()) {
            throw new DojoException("""
                    找到网关 %s，但没有 key。
                    在 .dojo/config 里补一行 api_key=你的key，或设置环境变量 DOJO_API_KEY。"""
                    .formatted(chosen.baseUrl()));
        }
        return new Gateway(chosen.baseUrl(), token, model, provider, chosen.source());
    }

    private static String firstModel(List<Gateway> sources) {
        return sources.stream()
                .map(Gateway::model)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static Gateway fromFile(Path file, String label) {
        if (!Files.isRegularFile(file)) {
            return new Gateway(null, null, null, null, label + "（不存在）");
        }
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String s = line.strip();
                if (s.isEmpty() || s.startsWith("#") || s.startsWith(";")) {
                    continue;
                }
                int eq = s.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                values.put(s.substring(0, eq).strip(), s.substring(eq + 1).strip());
            }
        } catch (IOException e) {
            throw new DojoException("读不了 " + file + "：" + e.getMessage(), e);
        }
        return fromMap(values, label);
    }

    private static Gateway fromMap(Map<String, String> values, String label) {
        String provider = firstNonBlank(values.get("provider"), values.get("protocol"));
        String base = firstNonBlank(values.get("base_url"), values.get("baseUrl"));
        String token = firstNonBlank(values.get("api_key"), values.get("auth_token"), values.get("token"));
        String model = firstNonBlank(values.get("model"), values.get("model_name"));
        return new Gateway(base, token, model, provider, label);
    }

    /** 复用用户已有的 Claude Code 网关，省掉一次配置。 */
    private static Gateway fromClaudeSettings(Path file) {
        if (!Files.isRegularFile(file)) {
            return new Gateway(null, null, null, null, "~/.claude/settings.json（不存在）");
        }
        try {
            Object root = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            Object envNode = Json.at(root, "env");
            if (envNode == null) {
                return new Gateway(null, null, null, null, "~/.claude/settings.json（无 env）");
            }
            Map<String, String> env = new LinkedHashMap<>();
            Json.obj(envNode).forEach((k, v) -> {
                if (v instanceof String s) {
                    env.put(k, s);
                }
            });
            String base = firstNonBlank(env.get("ANTHROPIC_BASE_URL"), env.get("ANTHROPIC_API_BASE"));
            String token = firstNonBlank(env.get("ANTHROPIC_AUTH_TOKEN"), env.get("ANTHROPIC_API_KEY"));
            String model = firstNonBlank(
                    env.get("ANTHROPIC_DEFAULT_SONNET_MODEL_NAME"),
                    env.get("ANTHROPIC_DEFAULT_OPUS_MODEL_NAME"),
                    env.get("ANTHROPIC_MODEL"));
            return new Gateway(base, token, stripBracket(model), "anthropic", "~/.claude/settings.json");
        } catch (IOException | RuntimeException e) {
            return new Gateway(null, null, null, null, "~/.claude/settings.json（解析失败：" + e.getMessage() + "）");
        }
    }

    private static Path homeConfig() {
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = xdg == null || xdg.isBlank()
                ? Path.of(System.getProperty("user.home"), ".config")
                : Path.of(xdg);
        return base.resolve("dojo/config");
    }

    private static String inferProvider(String baseUrl) {
        String b = baseUrl.toLowerCase();
        if (b.contains("anthropic")) {
            return "anthropic";
        }
        if (b.contains("openai") || b.contains("deepseek") || b.endsWith("/v1")) {
            return "openai";
        }
        return "anthropic";
    }

    static String stripBracket(String model) {
        if (model == null) {
            return null;
        }
        String s = model.strip();
        int start = s.indexOf('[');
        if (start >= 0) {
            s = s.substring(0, start).strip();
        }
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    /** 供 doctor 展示：所有候选来源（含被跳过的），不含明文 key。 */
    public static List<String> sourcesForDoctor(Repo repo) {
        List<String> out = new ArrayList<>();
        out.add("环境变量 DOJO_BASE_URL=" + (System.getenv("DOJO_BASE_URL") == null ? "（未设）" : "已设"));
        out.add("仓库配置 " + repo.root().resolve(".dojo/config") + (Files.isRegularFile(repo.root().resolve(".dojo/config")) ? "（存在）" : "（不存在）"));
        out.add("用户配置 " + homeConfig() + (Files.isRegularFile(homeConfig()) ? "（存在）" : "（不存在）"));
        out.add("Claude 配置 " + Path.of(System.getProperty("user.home"), ".claude", "settings.json")
                + "（" + (Files.isRegularFile(Path.of(System.getProperty("user.home"), ".claude", "settings.json")) ? "存在" : "不存在") + "）");
        return out;
    }
}
