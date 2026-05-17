package com.jobemailer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JobEmailerService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?<![A-Z0-9._%+-@])([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})(?![A-Z0-9._%+-@])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTEXTUAL_EMAIL_PATTERN = Pattern.compile(
            "(?:share\\s+(?:your\\s+)?(?:updated\\s+)?resume|send\\s+(?:your\\s+)?resume|updated\\s+resume|"
                    + "interested\\s+candidates|apply|reach\\s+out|contact|email|mailto|cv)\\s*(?:at|to|on)?\\s*:?\\s*"
                    + "([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})",
            Pattern.CASE_INSENSITIVE);

    private final JobEmailerProperties properties;
    private final TelegramClient telegramClient;
    private final LinkedInExtractor linkedInExtractor;
    private final GeminiClient geminiClient;
    private final EmailSender emailSender;
    private final JsonFileStore jsonFileStore;
    private final ObjectMapper objectMapper;

    public JobEmailerService(
            JobEmailerProperties properties,
            TelegramClient telegramClient,
            LinkedInExtractor linkedInExtractor,
            GeminiClient geminiClient,
            EmailSender emailSender,
            JsonFileStore jsonFileStore,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.linkedInExtractor = linkedInExtractor;
        this.geminiClient = geminiClient;
        this.emailSender = emailSender;
        this.jsonFileStore = jsonFileStore;
        this.objectMapper = objectMapper;
    }

    public void run() throws Exception {
        require(Path.of(properties.getCandidateContextFile()), "Candidate context file");
        require(Path.of(properties.getResumePath()), "Resume file");

        if (properties.getRunOnceUrl() != null && !properties.getRunOnceUrl().isBlank()) {
            ProcessResult result = processUrl(properties.getRunOnceUrl());
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            return;
        }

        if (properties.getTelegramBotToken() == null || properties.getTelegramBotToken().isBlank()) {
            throw new IllegalStateException("jobemailer.telegram-bot-token is required for polling mode");
        }

        long nextOffset = readLastUpdateId() + 1;
        System.out.println("[JobEmailer] Telegram polling started (offset=" + nextOffset + ")");
        long lastHeartbeatMs = 0;
        int consecutiveErrors = 0;
        while (true) {
            try {
                JsonNode updates = telegramClient.getUpdates(nextOffset).path("result");
                consecutiveErrors = 0;
                int updateCount = updates.size();
                long now = System.currentTimeMillis();
                if (now - lastHeartbeatMs >= 120_000) {
                    System.out.println("[JobEmailer] Polling alive at " + Instant.now()
                            + " (last batch: " + updateCount + " update(s))");
                    lastHeartbeatMs = now;
                }
                for (JsonNode update : updates) {
                    long updateId = update.path("update_id").asLong();
                    JsonNode message = update.path("message").isMissingNode() ? update.path("channel_post") : update.path("message");
                    long chatId = message.path("chat").path("id").asLong();
                    String text = message.path("text").asText(message.path("caption").asText(""));
                    if (chatId != 0 && !text.isBlank()) {
                        handleMessage(chatId, text);
                    }
                    nextOffset = updateId + 1;
                    writeLastUpdateId(updateId);
                }
            } catch (Exception e) {
                consecutiveErrors++;
                System.err.println("[JobEmailer] Polling error (#" + consecutiveErrors + " at " + Instant.now()
                        + "): " + e.getMessage());
                if (consecutiveErrors == 1 || consecutiveErrors % 10 == 0) {
                    e.printStackTrace(System.err);
                }
                Thread.sleep(properties.getPollIntervalSeconds() * 1000L);
            }
        }
    }

    private void handleMessage(long chatId, String text) throws IOException, InterruptedException {
        String url = extractLinkedInUrl(text);
        if (url.isEmpty()) {
            telegramClient.sendMessage(chatId, "Send a LinkedIn post URL. I will scrape it, draft an email, and optionally send it.");
            return;
        }
        telegramClient.sendMessage(chatId, "Processing the LinkedIn post. This can take a few seconds.");
        try {
            ProcessResult result = processUrl(url);
            telegramClient.sendMessage(chatId, formatSummary(result));
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, "Processing failed: " + e.getMessage());
        }
    }

    public ProcessResult processUrl(String url) throws Exception {
        String candidateContext = Files.readString(Path.of(properties.getCandidateContextFile()), StandardCharsets.UTF_8);
        PostData post = linkedInExtractor.extract(url);
        List<String> extractedEmails = extractEmails(post.getContent());
        String actualExtractedEmail = extractedEmails.isEmpty() ? "" : extractedEmails.get(0);
        if (actualExtractedEmail.isEmpty()) {
            throw new IllegalStateException("No email address could be extracted from the LinkedIn post content.");
        }

        String targetEmail = properties.isTestMode() ? properties.getTestRecipientOverride() : actualExtractedEmail;
        if (targetEmail == null || targetEmail.isBlank()) {
            throw new IllegalStateException("No target email is configured for sending.");
        }

        String cooldownKey = properties.isTestMode() ? "test::" + targetEmail.toLowerCase() : actualExtractedEmail.toLowerCase();
        CooldownStatus cooldownStatus = cooldownStatus(cooldownKey);
        EmailDraft draft = geminiClient.generateDraft(post, targetEmail, candidateContext);

        boolean emailSent = false;
        boolean cooldownSkipped = false;
        if (properties.isAutoSendEmail()) {
            if (cooldownStatus.active) {
                cooldownSkipped = true;
            } else {
                emailSender.sendEmail(targetEmail, draft.getSubject(), draft.getBody(), properties.getResumePath());
                recordSend(cooldownKey, actualExtractedEmail, targetEmail, url);
                emailSent = true;
            }
        }

        ProcessResult result = new ProcessResult();
        result.setLinkedinUrl(url);
        result.setPost(post);
        result.setExtractedPostEmails(extractedEmails);
        result.setActualExtractedEmail(actualExtractedEmail);
        result.setTargetEmail(targetEmail);
        result.setDraft(draft);
        result.setEmailSent(emailSent);
        result.setCooldownSkipped(cooldownSkipped);
        result.setCooldownRemainingDays(cooldownStatus.remainingDays);
        result.setTestMode(properties.isTestMode());
        result.setDefaultEmail(properties.getTestRecipientOverride());

        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("timestamp", Instant.now().getEpochSecond());
        historyEntry.put("linkedin_url", result.getLinkedinUrl());
        historyEntry.put("actual_extracted_email", result.getActualExtractedEmail());
        historyEntry.put("target_email", result.getTargetEmail());
        historyEntry.put("email_sent", result.isEmailSent());
        historyEntry.put("cooldown_skipped", result.isCooldownSkipped());
        historyEntry.put("test_mode", result.isTestMode());
        historyEntry.put("draft_subject", draft.getSubject());
        historyEntry.put("gemini_model", draft.getGeminiModel());
        historyEntry.put("gemini_key_index", draft.getGeminiKeyIndex());
        jsonFileStore.appendJsonLine(properties.getBotHistoryFile(), historyEntry);

        return result;
    }

    private String formatSummary(ProcessResult result) {
        String mode;
        if (result.isEmailSent()) {
            mode = "sent";
        } else if (result.isCooldownSkipped()) {
            mode = "skipped (cooldown active, " + result.getCooldownRemainingDays() + " day(s) left)";
        } else {
            mode = "drafted only";
        }

        String generationStatus;
        if (result.getDraft().getGeminiModel() != null && !result.getDraft().getGeminiModel().isBlank()) {
            generationStatus = "Email generated by Gemini using " + result.getDraft().getGeminiModel();
            if (result.getDraft().getGeminiKeyIndex() != null) {
                generationStatus += " (key " + result.getDraft().getGeminiKeyIndex() + ")";
            }
        } else {
            generationStatus = "Email generated by fallback logic";
        }

        return "Processed LinkedIn post.\n"
                + "Extracted email: " + result.getActualExtractedEmail() + "\n"
                + "Default/test email: " + result.getDefaultEmail() + "\n"
                + "Recipient used: " + result.getTargetEmail() + "\n"
                + "Test mode: " + result.isTestMode() + "\n"
                + "Mode: " + mode + "\n"
                + "Subject: " + result.getDraft().getSubject() + "\n"
                + "Post source: " + result.getPost().getSource() + "\n"
                + generationStatus;
    }

    private CooldownStatus cooldownStatus(String key) {
        Map<String, SentEmailRecord> history = jsonFileStore.readValue(
                properties.getSentEmailHistoryFile(),
                new TypeReference<Map<String, SentEmailRecord>>() {},
                new HashMap<>()
        );
        SentEmailRecord record = history.get(key);
        if (record == null) {
            return new CooldownStatus(false, 0);
        }
        long elapsedSeconds = Instant.now().getEpochSecond() - record.getLastSentAt();
        long cooldownSeconds = properties.getEmailCooldownDays() * 24L * 60L * 60L;
        if (elapsedSeconds < cooldownSeconds) {
            int remainingDays = (int) Math.max(1, (cooldownSeconds - elapsedSeconds + 86399) / 86400);
            return new CooldownStatus(true, remainingDays);
        }
        return new CooldownStatus(false, 0);
    }

    private void recordSend(String key, String actualEmail, String targetEmail, String url) throws IOException {
        Map<String, SentEmailRecord> history = jsonFileStore.readValue(
                properties.getSentEmailHistoryFile(),
                new TypeReference<Map<String, SentEmailRecord>>() {},
                new HashMap<>()
        );
        SentEmailRecord record = new SentEmailRecord();
        record.setActualExtractedEmail(actualEmail);
        record.setTargetEmail(targetEmail);
        record.setLinkedinUrl(url);
        record.setLastSentAt(Instant.now().getEpochSecond());
        record.setTestMode(properties.isTestMode());
        history.put(key, record);
        jsonFileStore.writeValue(properties.getSentEmailHistoryFile(), history);
    }

    private List<String> extractEmails(String text) {
        String normalized = normalizeTextForEmailExtraction(text);
        List<String> emails = new ArrayList<>();

        Matcher contextual = CONTEXTUAL_EMAIL_PATTERN.matcher(normalized);
        while (contextual.find()) {
            addUniqueEmail(emails, contextual.group(1));
        }

        Matcher matcher = EMAIL_PATTERN.matcher(normalized);
        while (matcher.find()) {
            addUniqueEmail(emails, matcher.group(1));
        }
        return emails;
    }

    /**
     * LinkedIn HTML/JSON often stores line breaks as the two characters '\' and 'n' between
     * "at:" and the address (e.g. {@code at:\nkeerthi@company.com}). Without normalization
     * the regex treats that {@code n} as part of the local part → {@code nkeerthi@...}.
     */
    private static String normalizeTextForEmailExtraction(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static void addUniqueEmail(List<String> emails, String email) {
        String normalized = email.toLowerCase();
        if (!emails.contains(normalized)) {
            emails.add(normalized);
        }
    }

    private String extractLinkedInUrl(String text) {
        Matcher matcher = Pattern.compile("https?://(?:www\\.)?linkedin\\.com/\\S+", Pattern.CASE_INSENSITIVE).matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group() : "";
    }

    private long readLastUpdateId() {
        Map<String, Object> state = jsonFileStore.readMap(properties.getTelegramStateFile());
        Object value = state.get("last_update_id");
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private void writeLastUpdateId(long updateId) throws IOException {
        Map<String, Object> state = new HashMap<>();
        state.put("last_update_id", updateId);
        jsonFileStore.writeValue(properties.getTelegramStateFile(), state);
    }

    private void require(Path path, String label) {
        if (!Files.exists(path)) {
            throw new IllegalStateException(label + " not found: " + path);
        }
    }

    private static final class CooldownStatus {
        private final boolean active;
        private final int remainingDays;

        private CooldownStatus(boolean active, int remainingDays) {
            this.active = active;
            this.remainingDays = remainingDays;
        }
    }
}
