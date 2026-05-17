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
        String query = "offset=" + offset + "&timeout=30";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase() + "/getUpdates?" + query))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return send(request);
    }

    public void sendMessage(long chatId, String text) throws IOException, InterruptedException {
        String body = "chat_id=" + chatId
                + "&disable_web_page_preview=true"
                + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase() + "/sendMessage"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        send(request);
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
        if (properties.getTelegramBotToken() == null || properties.getTelegramBotToken().isEmpty()) {
            throw new IllegalStateException("jobemailer.telegram-bot-token is required");
        }
        return "https://api.telegram.org/bot" + properties.getTelegramBotToken();
    }
}
