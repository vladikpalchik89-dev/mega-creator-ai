package com.megacreatorai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP-шлюз к Node.js-серверу. Секретов внутри нет: адрес и токен вводятся
 * пользователем в Профиле, токен хранится в SharedPreferences.
 * Все вызовы выполняются вне UI-потока (см. MainActivity.apiPost/apiGet).
 */
public final class ServerGateway {

    private final String baseUrl;
    private final String token;

    public ServerGateway(String baseUrl) {
        this(baseUrl, "");
    }

    public ServerGateway(String baseUrl, String token) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        this.baseUrl = value;
        this.token = token == null ? "" : token.trim();
    }

    public boolean isConfigured() {
        return baseUrl.startsWith("http://") || baseUrl.startsWith("https://");
    }

    public boolean hasToken() {
        return !token.isEmpty();
    }

    public String health(int timeoutMs) throws IOException {
        return request("GET", "/health", null, timeoutMs);
    }

    public String getJson(String path) throws IOException {
        return request("GET", path, null, 20000);
    }

    public String postJson(String path, String body) throws IOException {
        return request("POST", path, body, 30000);
    }

    public String postJson(String path, String body, int timeoutMs) throws IOException {
        return request("POST", path, body, timeoutMs);
    }

    public String deleteJson(String path) throws IOException {
        return request("DELETE", path, null, 20000);
    }

    private String request(String method, String path, String body, int timeoutMs) throws IOException {
        if (!isConfigured()) {
            throw new IOException("Адрес сервера не указан");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setUseCaches(false);
        if (hasToken()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
        }
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) {
                throw new IOException("Сервер вернул код " + code + " без ответа");
            }
            try (InputStream in = stream) {
                String response = read(in);
                if (code < 200 || code >= 300) {
                    String message = shortError(response);
                    throw new IOException("Ошибка сервера " + code + ": " + message);
                }
                return response;
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String shortError(String response) {
        if (response == null || response.isEmpty()) {
            return "без текста ошибки";
        }
        return response.length() > 180 ? response.substring(0, 180) : response;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
