package com.jobemailer;

public class EmailDraft {
    private String recipientName;
    private String subject;
    private String body;
    private String postSummary;
    private String geminiModel;
    private Integer geminiKeyIndex;
    private String fallbackReason;

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }   
    public String getPostSummary() { return postSummary; }
    public void setPostSummary(String postSummary) { this.postSummary = postSummary; }
    public String getGeminiModel() { return geminiModel; }
    public void setGeminiModel(String geminiModel) { this.geminiModel = geminiModel; }
    public Integer getGeminiKeyIndex() { return geminiKeyIndex; }
    public void setGeminiKeyIndex(Integer geminiKeyIndex) { this.geminiKeyIndex = geminiKeyIndex; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
}
