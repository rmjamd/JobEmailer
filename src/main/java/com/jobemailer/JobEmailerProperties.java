package com.jobemailer;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "jobemailer")
public class JobEmailerProperties {
    private String telegramBotToken;
    private String geminiApiKey;
    private List<String> geminiApiKeys = new ArrayList<>();
    private List<String> geminiModels = new ArrayList<>();
    private String smtpEmail;
    private String smtpPassword;
    private String smtpHost = "smtp.gmail.com";
    private int smtpPort = 465;
    private String resumePath;
    private String candidateContextFile = "./candidate_context.md";
    private boolean autoSendEmail;
    private boolean testMode = true;
    private String testRecipientOverride = "ramijnalpur@gmail.com";
    private String telegramStateFile = "./telegram_state.json";
    private String botHistoryFile = "./bot_history.jsonl";
    private String sentEmailHistoryFile = "./sent_email_history.json";
    private int emailCooldownDays = 7;
    private int pollIntervalSeconds = 2;
    private String yearsOfExperience = "5";
    private String runOnceUrl;

    public String getTelegramBotToken() { return telegramBotToken; }
    public void setTelegramBotToken(String telegramBotToken) { this.telegramBotToken = telegramBotToken; }
    public String getGeminiApiKey() { return geminiApiKey; }
    public void setGeminiApiKey(String geminiApiKey) { this.geminiApiKey = geminiApiKey; }
    public List<String> getGeminiApiKeys() { return geminiApiKeys; }
    public void setGeminiApiKeys(List<String> geminiApiKeys) { this.geminiApiKeys = geminiApiKeys; }
    public List<String> getGeminiModels() { return geminiModels; }
    public void setGeminiModels(List<String> geminiModels) { this.geminiModels = geminiModels; }
    public String getSmtpEmail() { return smtpEmail; }
    public void setSmtpEmail(String smtpEmail) { this.smtpEmail = smtpEmail; }
    public String getSmtpPassword() { return smtpPassword; }
    public void setSmtpPassword(String smtpPassword) { this.smtpPassword = smtpPassword; }
    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public void setSmtpPort(int smtpPort) { this.smtpPort = smtpPort; }
    public String getResumePath() { return resumePath; }
    public void setResumePath(String resumePath) { this.resumePath = resumePath; }
    public String getCandidateContextFile() { return candidateContextFile; }
    public void setCandidateContextFile(String candidateContextFile) { this.candidateContextFile = candidateContextFile; }
    public boolean isAutoSendEmail() { return autoSendEmail; }
    public void setAutoSendEmail(boolean autoSendEmail) { this.autoSendEmail = autoSendEmail; }
    public boolean isTestMode() { return testMode; }
    public void setTestMode(boolean testMode) { this.testMode = testMode; }
    public String getTestRecipientOverride() { return testRecipientOverride; }
    public void setTestRecipientOverride(String testRecipientOverride) { this.testRecipientOverride = testRecipientOverride; }
    public String getTelegramStateFile() { return telegramStateFile; }
    public void setTelegramStateFile(String telegramStateFile) { this.telegramStateFile = telegramStateFile; }
    public String getBotHistoryFile() { return botHistoryFile; }
    public void setBotHistoryFile(String botHistoryFile) { this.botHistoryFile = botHistoryFile; }
    public String getSentEmailHistoryFile() { return sentEmailHistoryFile; }
    public void setSentEmailHistoryFile(String sentEmailHistoryFile) { this.sentEmailHistoryFile = sentEmailHistoryFile; }
    public int getEmailCooldownDays() { return emailCooldownDays; }
    public void setEmailCooldownDays(int emailCooldownDays) { this.emailCooldownDays = emailCooldownDays; }
    public int getPollIntervalSeconds() { return pollIntervalSeconds; }
    public void setPollIntervalSeconds(int pollIntervalSeconds) { this.pollIntervalSeconds = pollIntervalSeconds; }
    public String getYearsOfExperience() { return yearsOfExperience; }
    public void setYearsOfExperience(String yearsOfExperience) { this.yearsOfExperience = yearsOfExperience; }
    public String getRunOnceUrl() { return runOnceUrl; }
    public void setRunOnceUrl(String runOnceUrl) { this.runOnceUrl = runOnceUrl; }
}
