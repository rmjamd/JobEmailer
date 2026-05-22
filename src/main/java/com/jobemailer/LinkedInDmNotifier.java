package com.jobemailer;

import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LinkedInDmNotifier {
    private final JobEmailerProperties properties;
    private final TelegramClient telegramClient;
    private final LinkedInDmChatResolver chatResolver;

    public LinkedInDmNotifier(
            JobEmailerProperties properties,
            TelegramClient telegramClient,
            LinkedInDmChatResolver chatResolver
    ) {
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.chatResolver = chatResolver;
    }

    public boolean isConfigured() {
        if (!properties.isLinkedinDmEnabled()) {
            return false;
        }
        if (properties.getLinkedinDmBotToken() == null || properties.getLinkedinDmBotToken().isBlank()) {
            return false;
        }
        return hasExplicitTarget() || hasChannelUsername();
    }

    private boolean hasExplicitTarget() {
        return properties.getLinkedinDmChatId() != null && !properties.getLinkedinDmChatId().isBlank();
    }

    private boolean hasChannelUsername() {
        return properties.getLinkedinDmChannel() != null && !properties.getLinkedinDmChannel().isBlank();
    }

    public void sendEmailDraft(PostData post, EmailDraft draft) throws IOException, InterruptedException {
        String chatTarget = chatResolver.resolveChatTarget();
        if (chatTarget == null || chatTarget.isBlank()) {
            throw new IllegalStateException(
                    "LinkedinDm channel is not configured. Set jobemailer.linkedin-dm-channel=@YourChannel in .env, "
                            + "or add @LinkedinDmBot as admin to the channel, post a message, and run "
                            + "./scripts/get-linkedin-dm-chat-id.sh"
            );
        }
        telegramClient.sendMessage(
                properties.getLinkedinDmBotToken(),
                chatTarget,
                formatTelegramMessage(post, draft)
        );
    }

    public String resolvedChannelLabel() throws IOException, InterruptedException {
        String target = chatResolver.resolveChatTarget();
        return target == null ? "LinkedinDm" : target;
    }

    static String formatTelegramMessage(PostData post, EmailDraft draft) {
        String author = post.getAuthor() == null || post.getAuthor().isBlank() ? "Unknown" : post.getAuthor();
        StringBuilder message = new StringBuilder();
        message.append("📧 No email in post — draft for LinkedIn outreach\n\n");
        message.append("Author: ").append(author).append('\n');
        message.append("URL: ").append(post.getUrl()).append("\n\n");
        if (draft.getPostSummary() != null && !draft.getPostSummary().isBlank()) {
            message.append("Summary: ").append(draft.getPostSummary()).append("\n\n");
        }
        message.append("Subject: ").append(draft.getSubject()).append("\n\n");
        message.append("📝 Draft email (copy for LinkedIn DM / InMail):\n");
        message.append("────────────────────\n");
        message.append(draft.getBody().trim()).append('\n');
        message.append("────────────────────");
        return message.toString();
    }
}
