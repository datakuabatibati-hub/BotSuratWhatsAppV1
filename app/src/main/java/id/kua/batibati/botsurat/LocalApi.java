package id.kua.batibati.botsurat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class LocalApi {
    public static final String BASE = "http://127.0.0.1:8765";
    private LocalApi() {}

    public static String get(String path) throws Exception {
        return request("GET", path, null);
    }

    public static String post(String path, String body) throws Exception {
        return request("POST", path, body == null ? "{}" : body);
    }

    public static String pairPath(String phone) {
        return "/pair?phone=" + URLEncoder.encode(phone, StandardCharsets.UTF_8);
    }

    private static String request(String method, String path, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(5000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 400 ? c.getErrorStream() : c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        if (code >= 400) throw new IllegalStateException("HTTP " + code + ": " + sb);
        return sb.toString();
    }
}
