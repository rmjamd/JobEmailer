package com.jobemailer;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class LinkedInDmChatResolver {
    private static final String STATE_FILE = "./linkedin_dm_state.json";
    private static final String STATE_KEY = "chat_id";

    private final JobEmailerProperties properties;
    private final TelegramClient telegramClient;
    private final JsonFileStore jsonFileStore;
    private String cachedTarget;

    public LinkedInDmChatResolver(
            JobEmailerProperties properties,
            TelegramClient telegramClient,
            JsonFileStore jsonFileStore
    ) {
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.jsonFileStore = jsonFileStore;
    }

    public String resolveChatTarget() throws IOException, InterruptedException {
        if (cachedTarget != null && !cachedTarget.isBlank()) {
            return cachedTarget;
        }

        if (properties.getLinkedinDmChatId() != null && !properties.getLinkedinDmChatId().isBlank()) {
            cachedTarget = properties.getLinkedinDmChatId().trim();
            return cachedTarget;
        }

        Map<String, Object> state = jsonFileStore.readMap(STATE_FILE);
        Object saved = state.get(STATE_KEY);
        if (saved != null && !saved.toString().isBlank()) {
            cachedTarget = saved.toString().trim();
            return cachedTarget;
        }

        if (properties.getLinkedinDmChannel() != null && !properties.getLinkedinDmChannel().isBlank()) {
            cachedTarget = properties.getLinkedinDmChannel().trim();
            return cachedTarget;
        }

        String discovered = discoverFromUpdates();
        if (discovered != null) {
            persistChatTarget(discovered);
            cachedTarget = discovered;
            return cachedTarget;
        }

        return null;
    }

    private String discoverFromUpdates() throws IOException, InterruptedException {
        String token = properties.getLinkedinDmBotToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        JsonNode results = telegramClient.getUpdates(token, 0).path("result");
        for (JsonNode update : results) {
            String fromChannel = chatIdFromUpdate(update, "channel_post");
            if (fromChannel != null) {
                return fromChannel;
            }
            String fromMessage = chatIdFromUpdate(update, "message");
            if (fromMessage != null) {
                return fromMessage;
            }
            String fromMembership = chatIdFromUpdate(update, "my_chat_member");
            if (fromMembership != null) {
                return fromMembership;
            }
        }
        return null;
    }

    private static String chatIdFromUpdate(JsonNode update, String field) {
        JsonNode node = update.path(field);
        if (node.isMissingNode()) {
            return null;
        }
        JsonNode chat = node.path("chat");
        if (chat.isMissingNode()) {
            chat = node.path("sender_chat");
        }
        if (chat.isMissingNode()) {
            return null;
        }
        if (chat.hasNonNull("id")) {
            return chat.path("id").asText();
        }
        return null;
    }

    private void persistChatTarget(String chatTarget) throws IOException {
        Map<String, Object> state = new HashMap<>();
        state.put(STATE_KEY, chatTarget);
        jsonFileStore.writeValue(STATE_FILE, state);
    }
}
