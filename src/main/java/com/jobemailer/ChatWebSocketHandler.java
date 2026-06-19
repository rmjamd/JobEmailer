package com.jobemailer;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final JobEmailerService service;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public ChatWebSocketHandler(JobEmailerService service) {
        this.service = service;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        send(session, "Connected. Paste a LinkedIn post URL to start.");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String text = message.getPayload() == null ? "" : message.getPayload().trim();
        if (text.isBlank()) {
            return;
        }
        executorService.submit(() -> {
            try {
                service.handleCustomUiMessage(text, response -> send(session, response));
            } catch (Exception e) {
                try {
                    send(session, "Processing failed: " + e.getMessage());
                } catch (Exception ignored) {
                    // The browser may already have disconnected.
                }
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("[JobEmailer] Chat socket disconnected at " + Instant.now()
                + " (" + status.getCode() + ")");
    }

    private void send(WebSocketSession session, String text) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(text));
            }
        }
    }
}
