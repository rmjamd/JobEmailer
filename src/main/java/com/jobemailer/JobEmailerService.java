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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JobEmailerService {
    private static final String PROVIDER_TELEGRAM = "telegram";
    private static final String PROVIDER_CUSTOM_UI = "custom-ui";
    private static final List<String> JOB_POST_MARKERS = List.of(
            "hiring", "role:", "role ", "position", "opening", "vacancy", "experience", "exp)", "yrs",
            "years", "location", "ctc", "lpa", "notice period", "apply", "resume", "cv", "job",
            "company name", "batch", "candidate", "salary", "recruit");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?<![A-Z0-9._%+@-])([A-Z0-9._%+-]+@(?:[A-Z0-9-]+\\.)+[A-Z]{2,})(?![A-Z0-9_%+@-])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTEXTUAL_EMAIL_PATTERN = Pattern.compile(
            "(?:share\\s+(?:your\\s+)?(?:updated\\s+)?resume|send\\s+(?:your\\s+)?resume|updated\\s+resume|"
                    + "interested\\s+candidates|apply|reach\\s+out|contact|email|mailto|cv)\\s*(?:at|to|on)?\\s*:?\\s*"
                    + "(?:the\\s+)?([A-Z0-9._%+-]+@(?:[A-Z0-9-]+\\.)+[A-Z]{2,})",
            Pattern.CASE_INSENSITIVE);

    private final JobEmailerProperties properties;
    private final TelegramClient telegramClient;
    private final LinkedInExtractor linkedInExtractor;
    private final GeminiClient geminiClient;
    private final EmailSender emailSender;
    private final LinkedInDmNotifier linkedInDmNotifier;
    private final JsonFileStore jsonFileStore;
    private final ObjectMapper objectMapper;
    private final ResourcePathResolver resourcePathResolver;
    private final ConcurrentHashMap<String, Object> sendLocks = new ConcurrentHashMap<>();

    public JobEmailerService(
            JobEmailerProperties properties,
            TelegramClient telegramClient,
            LinkedInExtractor linkedInExtractor,
            GeminiClient geminiClient,
            EmailSender emailSender,
            LinkedInDmNotifier linkedInDmNotifier,
            JsonFileStore jsonFileStore,
            ObjectMapper objectMapper,
            ResourcePathResolver resourcePathResolver
    ) {
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.linkedInExtractor = linkedInExtractor;
        this.geminiClient = geminiClient;
        this.emailSender = emailSender;
        this.linkedInDmNotifier = linkedInDmNotifier;
        this.jsonFileStore = jsonFileStore;
        this.objectMapper = objectMapper;
        this.resourcePathResolver = resourcePathResolver;
    }

    public void run() throws Exception {
        if (properties.getRunOnceUrl() != null && !properties.getRunOnceUrl().isBlank()) {
            requireRuntimeFiles();
            ProcessResult result = processUrl(properties.getRunOnceUrl(), 0L);
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            return;
        }

        List<String> providers = enabledProviders();
        boolean customUiEnabled = providers.contains(PROVIDER_CUSTOM_UI);
        boolean telegramEnabled = providers.contains(PROVIDER_TELEGRAM);
        if (!customUiEnabled && !telegramEnabled) {
            throw new IllegalStateException("jobemailer.input-provider must list at least one of "
                    + PROVIDER_TELEGRAM + " or " + PROVIDER_CUSTOM_UI
                    + " (got: '" + properties.getInputProvider() + "')");
        }

        if (customUiEnabled) {
            System.out.println("[JobEmailer] Custom chat UI enabled. Open http://localhost:8080/ (plugin socket: ws://localhost:8080/chat)");
        }
        if (telegramEnabled) {
            requireRuntimeFiles();
            if (properties.getTelegramBotToken() == null || properties.getTelegramBotToken().isBlank()) {
                throw new IllegalStateException("jobemailer.telegram-bot-token is required for polling mode");
            }
            startTelegramPolling();
        }
    }

    /**
     * Polling runs on its own thread so that enabling telegram alongside custom-ui does not block
     * the startup runner (and with it the WebSocket chat used by the browser plugin).
     */
    private void startTelegramPolling() {
        Thread poller = new Thread(() -> {
            try {
                pollTelegram();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[JobEmailer] Telegram polling interrupted");
            } catch (Exception e) {
                System.err.println("[JobEmailer] Telegram polling stopped: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }, "telegram-poller");
        poller.start();
    }

    private void pollTelegram() throws Exception {
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
                    JsonNode message = update.path("message");
                    if (!message.isMissingNode()) {
                        long chatId = message.path("chat").path("id").asLong();
                        String text = message.path("text").asText(message.path("caption").asText(""));
                        // Ignore bot-authored traffic and all channel posts to avoid re-processing
                        // the bot's own LinkedinDm fallback notifications as fresh LinkedIn jobs.
                        if (chatId != 0 && !text.isBlank() && !message.path("from").path("is_bot").asBoolean(false)) {
                            handleMessage(chatId, text);
                        }
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
        handleChatMessage(text, chatId, message -> telegramClient.sendMessage(chatId, message));
    }

    public void handleCustomUiMessage(String text, ChatResponder responder) throws IOException, InterruptedException {
        handleChatMessage(text, 0L, responder);
    }

    private void handleChatMessage(String text, long replyChatId, ChatResponder responder) throws IOException, InterruptedException {
        String url = extractLinkedInUrl(text);
        boolean pastedPost = url.isEmpty() && looksLikeJobPost(text);
        if (url.isEmpty() && !pastedPost) {
            responder.send("Send a LinkedIn post URL, or paste the job post text itself "
                    + "(include the recruiter email and I will apply to it directly).");
            return;
        }
        responder.send(pastedPost
                ? "Processing the pasted job post. This can take a few seconds."
                : "Processing the LinkedIn post. This can take a few seconds.");
        try {
            ProcessResult result = pastedPost ? processPastedContent(text, replyChatId) : processUrl(url, replyChatId);
            responder.send(formatSummary(result));
        } catch (Exception e) {
            responder.send("Processing failed: " + e.getMessage());
        }
    }

    /**
     * A message with no LinkedIn URL is treated as the post content when it carries a recruiter
     * email, or reads like a job post — so pasted "Company / Role / send CV to ..." blocks work
     * on both input providers without anything else being mistaken for a job.
     */
    private boolean looksLikeJobPost(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (!extractEmails(text).isEmpty()) {
            return true;
        }
        String lower = text.toLowerCase();
        int markerHits = 0;
        for (String marker : JOB_POST_MARKERS) {
            if (lower.contains(marker)) {
                markerHits++;
            }
        }
        return markerHits >= 2 && text.length() >= 40;
    }

    private static PostData buildPostFromPastedText(String text) {
        PostData post = new PostData();
        post.setUrl("");
        post.setAuthor("");
        post.setTimestamp("");
        post.setContent(text);
        post.setReactions("");
        post.setComments("");
        post.setTitle(firstNonBlankLine(text));
        post.setSource("pasted_text");
        return post;
    }

    private static String firstNonBlankLine(String text) {
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return "";
    }

    public ProcessResult processUrl(String url) throws Exception {
        return processUrl(url, 0L);
    }

    public ProcessResult processUrl(String url, long replyChatId) throws Exception {
        return processPost(linkedInExtractor.extract(url), replyChatId);
    }

    /** Treats a chat message that carries no LinkedIn URL as the post content itself. */
    public ProcessResult processPastedContent(String text, long replyChatId) throws Exception {
        return processPost(buildPostFromPastedText(text), replyChatId);
    }

    private ProcessResult processPost(PostData post, long replyChatId) throws Exception {
        String candidateContext = Files.readString(Path.of(properties.getCandidateContextFile()), StandardCharsets.UTF_8);
        String url = post.getUrl() == null ? "" : post.getUrl();
        List<String> extractedEmails = extractEmails(post.getContent());
        if (extractedEmails.isEmpty()) {
            return processUrlWithoutEmail(url, post, candidateContext, extractedEmails, replyChatId);
        }

        List<ProcessResult.EmailDelivery> deliveries = new ArrayList<>();
        for (String actualExtractedEmail : extractedEmails) {
            ProcessResult.EmailDelivery delivery = processEmailDelivery(url, post, candidateContext, actualExtractedEmail);
            deliveries.add(delivery);
        }

        ProcessResult.EmailDelivery firstDelivery = deliveries.get(0);

        ProcessResult result = new ProcessResult();
        result.setLinkedinUrl(url);
        result.setPost(post);
        result.setExtractedPostEmails(extractedEmails);
        result.setEmailDeliveries(deliveries);
        result.setActualExtractedEmail(firstDelivery.getActualExtractedEmail());
        result.setTargetEmail(firstDelivery.getTargetEmail());
        result.setDraft(firstDelivery.getDraft());
        result.setEmailSent(deliveries.stream().anyMatch(ProcessResult.EmailDelivery::isEmailSent));
        result.setCooldownSkipped(deliveries.stream().anyMatch(ProcessResult.EmailDelivery::isCooldownSkipped));
        result.setCooldownRemainingDays(deliveries.stream()
                .mapToInt(ProcessResult.EmailDelivery::getCooldownRemainingDays)
                .max()
                .orElse(0));
        result.setTestMode(properties.isTestMode());
        result.setDefaultEmail(properties.getTestRecipientOverride());

        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("timestamp", Instant.now().getEpochSecond());
        historyEntry.put("linkedin_url", result.getLinkedinUrl());
        historyEntry.put("actual_extracted_email", result.getActualExtractedEmail());
        historyEntry.put("actual_extracted_emails", extractedEmails);
        historyEntry.put("target_email", result.getTargetEmail());
        historyEntry.put("email_deliveries", deliveries);
        historyEntry.put("email_sent", result.isEmailSent());
        historyEntry.put("cooldown_skipped", result.isCooldownSkipped());
        historyEntry.put("test_mode", result.isTestMode());
        historyEntry.put("post_source", post.getSource());
        historyEntry.put("draft_subject", firstDelivery.getDraft().getSubject());
        historyEntry.put("gemini_model", firstDelivery.getDraft().getGeminiModel());
        historyEntry.put("gemini_key_index", firstDelivery.getDraft().getGeminiKeyIndex());
        jsonFileStore.appendJsonLine(properties.getBotHistoryFile(), historyEntry);

        return result;
    }

    private ProcessResult.EmailDelivery processEmailDelivery(
            String url,
            PostData post,
            String candidateContext,
            String actualExtractedEmail
    ) throws Exception {
        String targetEmail = properties.isTestMode() ? properties.getTestRecipientOverride() : actualExtractedEmail;
        if (targetEmail == null || targetEmail.isBlank()) {
            throw new IllegalStateException("No target email is configured for sending.");
        }

        String cooldownKey = properties.isTestMode()
                ? "test::" + actualExtractedEmail.toLowerCase() + "::" + targetEmail.toLowerCase()
                : actualExtractedEmail.toLowerCase();
        CooldownStatus cooldownStatus = cooldownStatus(cooldownKey);
        EmailDraft draft = geminiClient.generateDraft(post, targetEmail, candidateContext);

        boolean emailSent = false;
        boolean cooldownSkipped = false;
        if (properties.isAutoSendEmail()) {
            // Draft generation takes seconds, so the same recruiter can arrive on the other input
            // provider meanwhile. Re-check and record under a per-recipient lock so one address is
            // never mailed twice by telegram and the browser plugin at the same time.
            synchronized (sendLockFor(cooldownKey)) {
                cooldownStatus = cooldownStatus(cooldownKey);
                if (cooldownStatus.active) {
                    cooldownSkipped = true;
                } else {
                    String resumeAttachmentPath = resourcePathResolver
                            .materializeToPath(properties.getResumePath(), "Resume file")
                            .toString();
                    emailSender.sendEmail(targetEmail, draft.getSubject(), draft.getBody(), resumeAttachmentPath);
                    recordSend(cooldownKey, actualExtractedEmail, targetEmail, url);
                    emailSent = true;
                }
            }
        }

        ProcessResult.EmailDelivery delivery = new ProcessResult.EmailDelivery();
        delivery.setActualExtractedEmail(actualExtractedEmail);
        delivery.setTargetEmail(targetEmail);
        delivery.setDraft(draft);
        delivery.setEmailSent(emailSent);
        delivery.setCooldownSkipped(cooldownSkipped);
        delivery.setCooldownRemainingDays(cooldownStatus.remainingDays);
        return delivery;
    }

    private ProcessResult processUrlWithoutEmail(
            String url,
            PostData post,
            String candidateContext,
            List<String> extractedEmails,
            long replyChatId
    ) throws Exception {
        String pseudoRecipient = buildPseudoRecipientForDraft(post);
        EmailDraft emailDraft = geminiClient.generateDraft(post, pseudoRecipient, candidateContext);

        boolean linkedinDmSent = false;
        String linkedinDmChannel = "";
        String channelError = null;
        try {
            linkedInDmNotifier.sendEmailDraft(post, emailDraft);
            linkedinDmSent = true;
            linkedinDmChannel = linkedInDmNotifier.resolvedChannelLabel();
        } catch (Exception e) {
            channelError = e.getMessage();
            if (replyChatId != 0) {
                String draftMessage = LinkedInDmNotifier.formatTelegramMessage(post, emailDraft)
                        + "\n\n⚠️ LinkedinDm channel failed: " + channelError
                        + "\n(Add @LinkedinDmBot as admin to your LinkedinDm channel to fix.)";
                telegramClient.sendMessage(replyChatId, draftMessage);
                linkedinDmChannel = "this chat (LinkedinDm unavailable)";
            } else if (isCustomUiMode()) {
                linkedinDmChannel = "custom UI (LinkedinDm unavailable: " + channelError + ")";
            } else {
                throw e;
            }
        }

        ProcessResult result = new ProcessResult();
        result.setLinkedinUrl(url);
        result.setPost(post);
        result.setExtractedPostEmails(extractedEmails);
        result.setActualExtractedEmail("");
        result.setTargetEmail("");
        result.setDraft(emailDraft);
        result.setEmailSent(false);
        result.setCooldownSkipped(false);
        result.setCooldownRemainingDays(0);
        result.setTestMode(properties.isTestMode());
        result.setDefaultEmail(properties.getTestRecipientOverride());
        result.setLinkedinDmMode(true);
        result.setLinkedinDmSent(linkedinDmSent);
        result.setLinkedinDmChannel(linkedinDmChannel);

        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("timestamp", Instant.now().getEpochSecond());
        historyEntry.put("linkedin_url", url);
        historyEntry.put("actual_extracted_email", "");
        historyEntry.put("linkedin_dm_mode", true);
        historyEntry.put("linkedin_dm_sent", linkedinDmSent);
        historyEntry.put("linkedin_dm_channel", linkedinDmChannel);
        if (channelError != null) {
            historyEntry.put("linkedin_dm_error", channelError);
        }
        historyEntry.put("draft_subject", emailDraft.getSubject());
        historyEntry.put("draft_body_preview", truncate(emailDraft.getBody(), 200));
        historyEntry.put("gemini_model", emailDraft.getGeminiModel());
        historyEntry.put("gemini_key_index", emailDraft.getGeminiKeyIndex());
        jsonFileStore.appendJsonLine(properties.getBotHistoryFile(), historyEntry);

        return result;
    }

    private static String buildPseudoRecipientForDraft(PostData post) {
        String name = FallbackEmailGenerator.inferRecruiterNameFromPost(post).toLowerCase();
        return name + "@linkedin.post";
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private String formatSummary(ProcessResult result) {
        if (result.isLinkedinDmMode()) {
            return formatLinkedInDmSummary(result);
        }
        StringBuilder summary = new StringBuilder();
        summary.append("Processed LinkedIn post.\n")
                .append("Extracted emails: ")
                .append(String.join(", ", result.getExtractedPostEmails()))
                .append("\n")
                .append("Default/test email: ")
                .append(result.getDefaultEmail())
                .append("\n")
                .append("Test mode: ")
                .append(result.isTestMode())
                .append("\n")
                .append("Post source: ")
                .append(result.getPost().getSource())
                .append("\n\n")
                .append("Deliveries:");

        for (ProcessResult.EmailDelivery delivery : result.getEmailDeliveries()) {
            summary.append("\n- ")
                    .append(delivery.getActualExtractedEmail())
                    .append(" -> ")
                    .append(delivery.getTargetEmail())
                    .append(": ")
                    .append(formatDeliveryMode(delivery))
                    .append("\n  Subject: ")
                    .append(delivery.getDraft().getSubject())
                    .append("\n  ")
                    .append(formatGenerationStatus(delivery.getDraft()));
        }

        return summary.toString();
    }

    private String formatDeliveryMode(ProcessResult.EmailDelivery delivery) {
        if (delivery.isEmailSent()) {
            return "sent";
        }
        if (delivery.isCooldownSkipped()) {
            return "skipped (cooldown active, " + delivery.getCooldownRemainingDays() + " day(s) left)";
        }
        return "drafted only";
    }

    private String formatGenerationStatus(EmailDraft draft) {
        if (draft.getGeminiModel() != null && !draft.getGeminiModel().isBlank()) {
            String generationStatus = "Email generated by Gemini using " + draft.getGeminiModel();
            if (draft.getGeminiKeyIndex() != null) {
                generationStatus += " (key " + draft.getGeminiKeyIndex() + ")";
            }
            return generationStatus;
        }
        return "Email generated by fallback logic";
    }

    private String formatLinkedInDmSummary(ProcessResult result) {
        String generationStatus;
        if (result.getDraft().getGeminiModel() != null && !result.getDraft().getGeminiModel().isBlank()) {
            generationStatus = "Draft generated by Gemini using " + result.getDraft().getGeminiModel();
            if (result.getDraft().getGeminiKeyIndex() != null) {
                generationStatus += " (key " + result.getDraft().getGeminiKeyIndex() + ")";
            }
        } else {
            generationStatus = "Draft generated by fallback logic";
        }

        String delivery;
        if (result.isLinkedinDmSent()) {
            delivery = "Draft sent to LinkedinDm channel: " + result.getLinkedinDmChannel();
        } else if (result.getLinkedinDmChannel() != null && result.getLinkedinDmChannel().startsWith("this chat")) {
            delivery = "LinkedinDm channel failed — full draft sent here instead";
        } else {
            delivery = "Failed to send draft to LinkedinDm channel";
        }

        return "No email found in LinkedIn post.\n"
                + delivery + "\n"
                + "Post author: " + (result.getPost().getAuthor() == null ? "" : result.getPost().getAuthor()) + "\n"
                + "Post URL: " + result.getLinkedinUrl() + "\n"
                + "Subject: " + result.getDraft().getSubject() + "\n"
                + generationStatus + "\n\n"
                + "Draft preview:\n"
                + result.getDraft().getBody();
    }

    private Object sendLockFor(String cooldownKey) {
        return sendLocks.computeIfAbsent(cooldownKey, key -> new Object());
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

    private void requireRuntimeFiles() {
        require(Path.of(properties.getCandidateContextFile()), "Candidate context file");
        resourcePathResolver.requireExists(properties.getResumePath(), "Resume file");
    }

    private boolean isCustomUiMode() {
        return enabledProviders().contains(PROVIDER_CUSTOM_UI);
    }

    /**
     * {@code jobemailer.input-provider} accepts one or more providers, e.g. {@code custom-ui,telegram},
     * so the browser plugin and Telegram polling can serve the same running app.
     */
    private List<String> enabledProviders() {
        List<String> providers = new ArrayList<>();
        String raw = properties.getInputProvider();
        if (raw == null) {
            return providers;
        }
        for (String part : raw.split("[,;\\s]+")) {
            String normalized = part.trim().toLowerCase();
            if (normalized.isEmpty()) {
                continue;
            }
            if (normalized.equals("custom")) {
                normalized = PROVIDER_CUSTOM_UI;
            }
            if (!providers.contains(normalized)) {
                providers.add(normalized);
            }
        }
        return providers;
    }

    @FunctionalInterface
    public interface ChatResponder {
        void send(String message) throws IOException, InterruptedException;
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
