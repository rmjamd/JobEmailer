package com.jobemailer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LinkedInExtractor {
    private static final Pattern ARTICLE_BODY = Pattern.compile("\"articleBody\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern OG_TITLE = Pattern.compile("<meta property=\"og:title\" content=\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern COMMENT_COUNT = Pattern.compile("\"commentCount\":(\\d+)");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public LinkedInExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PostData extract(String url) throws IOException, InterruptedException {
        String html = fetch(url);
        PostData post = new PostData();
        post.setUrl(url);
        post.setAuthor(extractAuthor(html));
        post.setTitle(extractTitle(html));
        post.setComments(extractComments(html));
        post.setContent(extractContent(html));
        post.setReactions("");
        post.setTimestamp("");
        post.setSource("public_html_fallback");
        return post;
    }

    private String fetch(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 300) {
            throw new IOException("LinkedIn fetch failed: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String extractContent(String html) throws IOException {
        Matcher matcher = ARTICLE_BODY.matcher(html);
        if (matcher.find()) {
            String escaped = matcher.group(1).replace("\\", "\\\\").replace("\"", "\\\"");
            return objectMapper.readValue("\"" + escaped + "\"", String.class).trim();
        }
        return "";
    }

    private String extractTitle(String html) {
        Matcher matcher = OG_TITLE.matcher(html);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String extractAuthor(String html) {
        String title = extractTitle(html);
        String[] parts = title.split("\\|");
        return parts.length >= 2 ? parts[1].trim() : "";
    }

    private String extractComments(String html) {
        Matcher matcher = COMMENT_COUNT.matcher(html);
        return matcher.find() ? matcher.group(1) + " comments" : "";
    }
}
