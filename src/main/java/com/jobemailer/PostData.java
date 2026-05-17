package com.jobemailer;

public class PostData {
    private String url;
    private String author;
    private String timestamp;
    private String content;
    private String reactions;
    private String comments;
    private String title;
    private String source;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getReactions() { return reactions; }
    public void setReactions(String reactions) { this.reactions = reactions; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
