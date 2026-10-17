package dojo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigTest {

    private static Config.Gateway gateway(String base, String provider) {
        return new Config.Gateway(base, "sk-1234567890", "deepseek-v4.1-flash", provider, "test");
    }

    @Test
    void anthropicEndpoints() {
        assertEquals("https://x.com/v1/messages", gateway("https://x.com", "anthropic").endpoint());
        assertEquals("https://x.com/v1/messages", gateway("https://x.com/", "anthropic").endpoint());
        assertEquals("https://x.com/v1/messages", gateway("https://x.com/v1", "anthropic").endpoint());
        assertEquals("https://x.com/v1/messages", gateway("https://x.com/v1/messages", "anthropic").endpoint());
        assertEquals("https://x.com/apps/anthropic/v1/messages",
                gateway("https://x.com/apps/anthropic", "anthropic").endpoint());
    }

    @Test
    void openaiEndpoints() {
        assertEquals("https://x.com/v1/chat/completions", gateway("https://x.com", "openai").endpoint());
        assertEquals("https://x.com/v1/chat/completions", gateway("https://x.com/v1", "openai").endpoint());
        assertEquals("https://x.com/v1/chat/completions",
                gateway("https://x.com/v1/chat/completions", "openai").endpoint());
    }

    @Test
    void masksTokens() {
        assertEquals("…7890（13 位）", gateway("https://x.com", "anthropic").maskedToken());
        assertEquals("****（3 位）", new Config.Gateway("https://x.com", "abc", "m", "anthropic", "t").maskedToken());
        assertEquals("（缺失）", new Config.Gateway("https://x.com", "", "m", "anthropic", "t").maskedToken());
    }
}
