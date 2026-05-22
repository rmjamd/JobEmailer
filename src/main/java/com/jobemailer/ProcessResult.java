package com.jobemailer;

import java.util.List;

public class ProcessResult {
    private String linkedinUrl;
    private PostData post;
    private List<String> extractedPostEmails;
    private String actualExtractedEmail;
    private String targetEmail;
    private EmailDraft draft;
    private boolean emailSent;
    private boolean cooldownSkipped;
    private int cooldownRemainingDays;
    private boolean testMode;
    private String defaultEmail;
    private boolean linkedinDmMode;
    private boolean linkedinDmSent;
    private String linkedinDmChannel;

    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public PostData getPost() { return post; }
    public void setPost(PostData post) { this.post = post; }
    public List<String> getExtractedPostEmails() { return extractedPostEmails; }
    public void setExtractedPostEmails(List<String> extractedPostEmails) { this.extractedPostEmails = extractedPostEmails; }
    public String getActualExtractedEmail() { return actualExtractedEmail; }
    public void setActualExtractedEmail(String actualExtractedEmail) { this.actualExtractedEmail = actualExtractedEmail; }
    public String getTargetEmail() { return targetEmail; }
    public void setTargetEmail(String targetEmail) { this.targetEmail = targetEmail; }
    public EmailDraft getDraft() { return draft; }
    public void setDraft(EmailDraft draft) { this.draft = draft; }
    public boolean isEmailSent() { return emailSent; }
    public void setEmailSent(boolean emailSent) { this.emailSent = emailSent; }
    public boolean isCooldownSkipped() { return cooldownSkipped; }
    public void setCooldownSkipped(boolean cooldownSkipped) { this.cooldownSkipped = cooldownSkipped; }
    public int getCooldownRemainingDays() { return cooldownRemainingDays; }
    public void setCooldownRemainingDays(int cooldownRemainingDays) { this.cooldownRemainingDays = cooldownRemainingDays; }
    public boolean isTestMode() { return testMode; }
    public void setTestMode(boolean testMode) { this.testMode = testMode; }
    public String getDefaultEmail() { return defaultEmail; }
    public void setDefaultEmail(String defaultEmail) { this.defaultEmail = defaultEmail; }
    public boolean isLinkedinDmMode() { return linkedinDmMode; }
    public void setLinkedinDmMode(boolean linkedinDmMode) { this.linkedinDmMode = linkedinDmMode; }
    public boolean isLinkedinDmSent() { return linkedinDmSent; }
    public void setLinkedinDmSent(boolean linkedinDmSent) { this.linkedinDmSent = linkedinDmSent; }
    public String getLinkedinDmChannel() { return linkedinDmChannel; }
    public void setLinkedinDmChannel(String linkedinDmChannel) { this.linkedinDmChannel = linkedinDmChannel; }
}
