package com.jobemailer;

public class SentEmailRecord {
    private String actualExtractedEmail;
    private String targetEmail;
    private String linkedinUrl;
    private long lastSentAt;
    private boolean testMode;

    public String getActualExtractedEmail() { return actualExtractedEmail; }
    public void setActualExtractedEmail(String actualExtractedEmail) { this.actualExtractedEmail = actualExtractedEmail; }
    public String getTargetEmail() { return targetEmail; }
    public void setTargetEmail(String targetEmail) { this.targetEmail = targetEmail; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public long getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(long lastSentAt) { this.lastSentAt = lastSentAt; }
    public boolean isTestMode() { return testMode; }
    public void setTestMode(boolean testMode) { this.testMode = testMode; }
}
