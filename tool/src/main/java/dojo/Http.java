package dojo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** 只做一件事：POST 一段 JSON，把状态码和响应体原样带回来。 */
public final class Http {

    public record Response(int status, String body) {

        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    private static final HttpClient CLIENT = buildClient();

    private Http() {
    }

    private static HttpClient buildClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL);
        String proxy = System.getenv("DOJO_PROXY");
        if (proxy != null && !proxy.isBlank()) {
            String hostPort = proxy.replaceFirst("^https?://", "");
            int colon = hostPort.lastIndexOf(':');
            String host = colon < 0 ? hostPort : hostPort.substring(0, colon);
            int port = colon < 0 ? 7890 : Integer.parseInt(hostPort.substring(colon + 1));
            builder.proxy(ProxySelector.of(new InetSocketAddress(host, port)));
        }
        return builder.build();
    }

    public static Response postJson(String url, String body, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder request;
        try {
            request = HttpRequest.newBuilder(URI.create(url));
        } catch (IllegalArgumentException e) {
            throw new DojoException("地址不合法：" + url);
        }
        request.timeout(timeout)
                .header("content-type", "application/json; charset=utf-8")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(request::header);

        try {
            HttpResponse<String> response =
                    CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            String hint = "  · 连不上 " + hostOf(url) + "。检查网络；如果本机要走代理，设置 DOJO_PROXY=http://127.0.0.1:7890";
            throw new DojoException("网络请求失败：" + e.getMessage() + "\n" + hint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DojoException("请求被中断");
        }
    }

    public static Response get(String url, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder request;
        try {
            request = HttpRequest.newBuilder(URI.create(url));
        } catch (IllegalArgumentException e) {
            throw new DojoException("地址不合法：" + url);
        }
        request.timeout(timeout).header("accept", "*/*").GET();
        headers.forEach(request::header);
        try {
            HttpResponse<String> response =
                    CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new DojoException("网络请求失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DojoException("请求被中断");
        }
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }
}
