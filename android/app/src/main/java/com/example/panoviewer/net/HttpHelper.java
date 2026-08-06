package com.example.panoviewer.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 极简 HTTP 客户端封装（零三方依赖，仅用 JDK {@link HttpURLConnection}）。
 *
 * <p>能力：</p>
 * <ul>
 *   <li>{@link #getBytes(String)} / {@link #getString(String)} / {@link #postJson(String, String)}；</li>
 *   <li>内存 Cookie Jar（按 host 记忆 Set-Cookie，随请求回带）；</li>
 *   <li>跟随 302/301/307/308 重定向（最多 5 次）；</li>
 *   <li>15s 连接/读超时；</li>
 *   <li>自动处理 gzip 响应体。</li>
 * </ul>
 *
 * <p>构造可注入，便于在单测中用 fake 子类替换网络行为。</p>
 */
public class HttpHelper {

    /** 连接 / 读超时（毫秒）。 */
    public static final int DEFAULT_TIMEOUT_MS = 15000;

    private final Map<String, String> cookieJar = new HashMap<>();

    /** 构造一个默认配置的 HttpHelper。 */
    public HttpHelper() {
        // 默认构造；无状态依赖，可直接 new。
    }

    /**
     * 下载原始字节。
     *
     * @param urlStr 请求地址
     * @return 响应体字节
     * @throws IOException 网络错误或 HTTP 非 2xx
     */
    @NonNull
    public byte[] getBytes(@NonNull String urlStr) throws IOException {
        return request(urlStr, "GET", null, null);
    }

    /**
     * 下载为字符串（UTF-8 解码）。
     *
     * @param urlStr 请求地址
     * @return 响应体文本
     * @throws IOException 网络错误或 HTTP 非 2xx
     */
    @NonNull
    public String getString(@NonNull String urlStr) throws IOException {
        return new String(getBytes(urlStr), StandardCharsets.UTF_8);
    }

    /**
     * 以 {@code application/json} 提交 JSON 并取回文本响应。
     *
     * @param urlStr   请求地址
     * @param jsonBody JSON 请求体
     * @return 响应体文本
     * @throws IOException 网络错误或 HTTP 非 2xx
     */
    @NonNull
    public String postJson(@NonNull String urlStr, @NonNull String jsonBody) throws IOException {
        return new String(
                request(urlStr, "POST", jsonBody, "application/json; charset=utf-8"),
                StandardCharsets.UTF_8);
    }

    /**
     * 统一请求实现：处理重定向、Cookie、gzip。
     */
    @NonNull
    private byte[] request(
            @NonNull String urlStr,
            @NonNull String method,
            @Nullable String body,
            @Nullable String contentType) throws IOException {
        String current = urlStr;
        int redirects = 0;
        while (true) {
            URL url = new URL(current);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(DEFAULT_TIMEOUT_MS);
            conn.setReadTimeout(DEFAULT_TIMEOUT_MS);
            conn.setRequestMethod(method);
            // 关闭自动跟随，自行处理重定向以便保留 Cookie 与最终 URL
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setRequestProperty("User-Agent", "PanoViewer/1.0 (Android)");
            conn.setRequestProperty("Accept", "*/*");

            String cookies = buildCookieHeader(url);
            if (!cookies.isEmpty()) {
                conn.setRequestProperty("Cookie", cookies);
            }

            if (body != null) {
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", contentType);
                conn.setRequestProperty("Content-Length", String.valueOf(payload.length));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
            }

            int code = conn.getResponseCode();
            storeCookies(url, conn.getHeaderFields());

            if (isRedirect(code) && redirects < 5) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) {
                    break;
                }
                current = location.startsWith("http")
                        ? location
                        : new URL(url, location).toString();
                redirects++;
                continue;
            }

            InputStream raw = (code >= 200 && code < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            byte[] data = readStream(raw, conn);
            if (raw != null) {
                raw.close();
            }
            conn.disconnect();

            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " for " + current);
            }
            return data;
        }
        throw new IOException("Too many redirects for " + urlStr);
    }

    private static boolean isRedirect(int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == 307
                || code == 308;
    }

    @NonNull
    private byte[] readStream(@Nullable InputStream in, @NonNull HttpURLConnection conn)
            throws IOException {
        if (in == null) {
            return new byte[0];
        }
        InputStream wrapped = "gzip".equalsIgnoreCase(conn.getContentEncoding())
                ? new GZIPInputStream(in)
                : in;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = wrapped.read(buf)) > 0) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private synchronized void storeCookies(@NonNull URL url, @NonNull Map<String, List<String>> headers) {
        List<String> setCookies = headers.get("Set-Cookie");
        if (setCookies == null) {
            setCookies = headers.get("set-cookie");
        }
        if (setCookies == null || setCookies.isEmpty()) {
            return;
        }
        String domain = url.getHost();
        List<String> pairs = new ArrayList<>();
        for (String sc : setCookies) {
            int semi = sc.indexOf(';');
            String pair = semi >= 0 ? sc.substring(0, semi) : sc;
            if (!pair.trim().isEmpty()) {
                pairs.add(pair.trim());
            }
        }
        if (!pairs.isEmpty()) {
            cookieJar.put(domain, String.join("; ", pairs));
        }
    }

    @NonNull
    private synchronized String buildCookieHeader(@NonNull URL url) {
        String c = cookieJar.get(url.getHost());
        return c == null ? "" : c;
    }
}
