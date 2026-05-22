package com.jobemailer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class TelegramClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    /** Telegram long-poll timeout is 30s; allow extra margin for slow networks. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

    private final JobEmailerProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public TelegramClient(JobEmailerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public JsonNode getUpdates(long offset) throws IOException, InterruptedException {
        return getUpdates(properties.getTelegramBotToken(), offset);
    }

    public JsonNode getUpdates(String botToken, long offset) throws IOException, InterruptedException {
        String query = "offset=" + offset + "&timeout=30";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase(botToken) + "/getUpdates?" + query))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return send(request);
    }

    public void sendMessage(long chatId, String text) throws IOException, InterruptedException {
        sendMessage(properties.getTelegramBotToken(), String.valueOf(chatId), text);
    }

    public void sendMessage(String botToken, String chatId, String text) throws IOException, InterruptedException {
        for (String chunk : splitTelegramMessage(text)) {
            String body = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                    + "&disable_web_page_preview=true"
                    + "&text=" + URLEncoder.encode(chunk, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase(botToken) + "/sendMessage"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            send(request);
        }
    }

    static List<String> splitTelegramMessage(String text) {
        List<String> chunks = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            chunks.add("");
            return chunks;
        }
        final int limit = 4000;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + limit, text.length());
            if (end < text.length()) {
                int breakAt = text.lastIndexOf('\n', end);
                if (breakAt <= start) {
                    breakAt = end;
                }
                end = breakAt;
            }
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private JsonNode send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("Telegram HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode node = objectMapper.readTree(response.body());
        if (!node.path("ok").asBoolean()) {
            throw new IOException("Telegram API error: " + response.body());
        }
        return node;
    }

    private String apiBase() {
        return apiBase(properties.getTelegramBotToken());
    }

    private static String apiBase(String botToken) {
        if (botToken == null || botToken.isEmpty()) {
            throw new IllegalStateException("Telegram bot token is required");
        }
        return "https://api.telegram.org/bot" + botToken;
    }
}
