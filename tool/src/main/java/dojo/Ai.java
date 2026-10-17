package dojo;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 一次对话补全。两种线上协议：Anthropic Messages 与 OpenAI Chat Completions。
 *
 * <p>这里只负责「把请求发出去、把文本取回来」。重试、加预算、泄底重写这些决策
 * 都在 Ladder 里，便于测试，也便于 `dojo prompt` 展示真实请求。
 */
public final class Ai {

    public record Reply(String text, boolean truncated, int inputTokens, int outputTokens, long millis) {
    }

    private final Config.Gateway gateway;

    public Ai(Config.Gateway gateway) {
        this.gateway = gateway;
    }

    public String model() {
        return gateway.model();
    }

    public Reply complete(String system, String user, int maxTokens) {
        String body = isAnthropic()
                ? anthropicBody(system, user, maxTokens)
                : openAiBody(system, user, maxTokens);

        long start = System.currentTimeMillis();
        Http.Response response = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                response = Http.postJson(gateway.endpoint(), body, headers(), Duration.ofSeconds(timeoutSeconds()));
            } catch (DojoException e) {
                if (attempt == 3) {
                    throw e;
                }
                sleep(attempt * 1000L);
                continue;
            }
            if (response.ok()) {
                break;
            }
            boolean retryable = response.status() == 429 || response.status() >= 500;
            if (!retryable || attempt == 3) {
                throw httpError(response);
            }
            sleep(attempt * 1000L);
        }
        if (response == null || !response.ok()) {
            throw new DojoException("模型请求失败（没有拿到任何响应）");
        }
        long millis = System.currentTimeMillis() - start;
        return isAnthropic() ? parseAnthropic(response.body(), millis) : parseOpenAi(response.body(), millis);
    }

    private boolean isAnthropic() {
        return !"openai".equals(gateway.provider());
    }

    private String anthropicBody(String system, String user, int maxTokens) {
        return "{\"model\":" + Json.quote(gateway.model())
                + ",\"max_tokens\":" + maxTokens
                + ",\"system\":" + Json.quote(system)
                + ",\"messages\":[{\"role\":\"user\",\"content\":" + Json.quote(user) + "}]}";
    }

    private String openAiBody(String system, String user, int maxTokens) {
        return "{\"model\":" + Json.quote(gateway.model())
                + ",\"max_tokens\":" + maxTokens
                + ",\"messages\":["
                + "{\"role\":\"system\",\"content\":" + Json.quote(system) + "},"
                + "{\"role\":\"user\",\"content\":" + Json.quote(user) + "}]}";
    }

    private Map<String, String> headers() {
        if (isAnthropic()) {
            // 两个头都发：标准 Anthropic 用 x-api-key，部分国内网关只认 Bearer。
            return Map.of(
                    "x-api-key", gateway.token(),
                    "authorization", "Bearer " + gateway.token(),
                    "anthropic-version", "2023-06-01");
        }
        return Map.of("authorization", "Bearer " + gateway.token());
    }

    private Reply parseAnthropic(String body, long millis) {
        Object root = parseOrThrow(body);
        Object content = Json.at(root, "content");
        StringBuilder text = new StringBuilder();
        boolean sawThinking = false;
        if (content instanceof List<?> blocks) {
            for (Object block : blocks) {
                String type = Json.strAt(block, "type");
                if ("thinking".equals(type) || "redacted_thinking".equals(type)) {
                    sawThinking = true;
                    continue;
                }
                String chunk = Json.strAt(block, "text");
                if (chunk != null) {
                    text.append(chunk);
                }
            }
        }
        String stop = Json.strAt(root, "stop_reason");
        boolean truncated = "max_tokens".equals(stop) || (text.toString().isBlank() && sawThinking);
        if (text.toString().isBlank() && !sawThinking) {
            throw new DojoException("模型没有返回文本（stop_reason=" + stop + "）。原始响应片段："
                    + excerpt(body));
        }
        return new Reply(text.toString().strip(), truncated, intAt(root, "usage", "input_tokens"),
                intAt(root, "usage", "output_tokens"), millis);
    }

    private Reply parseOpenAi(String body, long millis) {
        Object root = parseOrThrow(body);
        Object choices = Json.at(root, "choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            throw new DojoException("模型响应里没有 choices：" + excerpt(body));
        }
        Object message = Json.at(list.get(0), "message");
        String text = Json.strAt(message, "content");
        if (text == null) {
            text = "";
        }
        String finish = Json.strAt(list.get(0), "finish_reason");
        boolean truncated = "length".equals(finish);
        if (text.isBlank() && !truncated) {
            String reasoning = Json.strAt(message, "reasoning_content");
            if (reasoning != null && !reasoning.isBlank()) {
                throw new DojoException("模型只返回了推理过程、没有正文（finish_reason=" + finish + "）。"
                        + "把 max_tokens 调大，或换一个非推理模型。");
            }
            throw new DojoException("模型返回了空文本（finish_reason=" + finish + "）。");
        }
        return new Reply(text.strip(), truncated, intAt(root, "usage", "prompt_tokens"),
                intAt(root, "usage", "completion_tokens"), millis);
    }

    private Object parseOrThrow(String body) {
        try {
            return Json.parse(body);
        } catch (IllegalArgumentException e) {
            throw new DojoException("模型响应不是 JSON：" + excerpt(body), e);
        }
    }

    private DojoException httpError(Http.Response response) {
        String detail = excerpt(response.body());
        String hint = switch (response.status()) {
            case 401, 403 -> "\n  · key 不对或没权限。检查 .dojo/config 的 api_key，或 `dojo doctor` 看实际来源。";
            case 404 -> "\n  · 路径不对。检查 base_url 与 provider（anthropic 会拼 /v1/messages，openai 会拼 /v1/chat/completions）。";
            case 429 -> "\n  · 被限流了，等几分钟再试。";
            default -> "";
        };
        return new DojoException("模型网关返回 HTTP " + response.status() + "：" + detail + hint);
    }

    private int timeoutSeconds() {
        String raw = System.getenv("DOJO_TIMEOUT_SEC");
        if (raw == null) {
            return 180;
        }
        try {
            return Math.max(10, Integer.parseInt(raw.strip()));
        } catch (NumberFormatException e) {
            return 180;
        }
    }

    private static int intAt(Object node, String... path) {
        Object value = Json.at(node, path);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String excerpt(String body) {
        if (body == null) {
            return "（空响应）";
        }
        String flat = body.replaceAll("\\s+", " ").strip();
        return flat.length() <= 400 ? flat : flat.substring(0, 400) + "…";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
