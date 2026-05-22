package com.jobemailer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class GeminiClient {
    private final JobEmailerProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public GeminiClient(JobEmailerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public EmailDraft generateLinkedInDm(PostData post, String candidateContext) {
        List<String> failures = new ArrayList<>();
        List<String> keys = geminiKeys();
        List<String> models = properties.getGeminiModels();

        for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
            String apiKey = keys.get(keyIndex);
            for (String model : models) {
                try {
                    EmailDraft draft = call(apiKey, model, buildLinkedInDmPrompt(post, candidateContext));
                    draft.setGeminiModel(model);
                    draft.setGeminiKeyIndex(keyIndex + 1);
                    return draft;
                } catch (Exception e) {
                    failures.add("key" + (keyIndex + 1) + "/" + model + ": " + e.getMessage());
                }
            }
        }

        EmailDraft fallback = FallbackEmailGenerator.generateLinkedInDm(post, properties);
        fallback.setFallbackReason(String.join("; ", failures));
        return fallback;
    }

    public EmailDraft generateDraft(PostData post, String recipientEmail, String candidateContext) {
        List<String> failures = new ArrayList<>();
        List<String> keys = geminiKeys();
        List<String> models = properties.getGeminiModels();

        for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
            String apiKey = keys.get(keyIndex);
            for (String model : models) {
                try {
                    EmailDraft draft = call(apiKey, model, buildPrompt(post, recipientEmail, candidateContext));
                    draft.setGeminiModel(model);
                    draft.setGeminiKeyIndex(keyIndex + 1);
                    return draft;
                } catch (Exception e) {
                    failures.add("key" + (keyIndex + 1) + "/" + model + ": " + e.getMessage());
                }
            }
        }

        EmailDraft fallback = FallbackEmailGenerator.generate(post, recipientEmail, properties);
        fallback.setFallbackReason(String.join("; ", failures));
        return fallback;
    }

    private EmailDraft call(String apiKey, String model, String prompt) throws IOException, InterruptedException {
        JsonNode payload = objectMapper.createObjectNode()
                .set("contents", objectMapper.createArrayNode().add(
                        objectMapper.createObjectNode().set("parts", objectMapper.createArrayNode().add(
                                objectMapper.createObjectNode().put("text", prompt)
                        ))
                ));
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).set(
                "generationConfig",
                objectMapper.createObjectNode().put("temperature", 0.4).put("responseMimeType", "application/json")
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (textNode.isMissingNode() || textNode.asText().isEmpty()) {
            throw new IOException("Gemini returned empty text");
        }

        String text = textNode.asText().trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z0-9_-]*\\n", "").replaceFirst("\\n```$", "");
        }
        JsonNode parsed = objectMapper.readTree(text);
        EmailDraft draft = new EmailDraft();
        draft.setRecipientName(parsed.path("recipient_name").asText("there"));
        draft.setSubject(parsed.path("subject").asText(""));
        draft.setBody(parsed.path("body").asText(""));
        draft.setPostSummary(parsed.path("post_summary").asText(""));
        return draft;
    }

    private String buildLinkedInDmPrompt(PostData post, String candidateContext) throws IOException {
        JsonNode signals = objectMapper.createObjectNode()
                .put("tier1_requested", FallbackEmailGenerator.postRequiresTier1(post))
                .put("strong_dsa_requested", FallbackEmailGenerator.postRequiresStrongDsa(post));

        String recruiterName = FallbackEmailGenerator.inferRecruiterNameFromPost(post);

        return "You are helping a candidate write a short LinkedIn direct message to a recruiter/hiring manager.\n\n"
                + "Return only valid JSON with this exact schema:\n"
                + "{\n"
                + "  \"recipient_name\": \"string\",\n"
                + "  \"subject\": \"\",\n"
                + "  \"body\": \"string\",\n"
                + "  \"post_summary\": \"string\"\n"
                + "}\n\n"
                + "Rules:\n"
                + "- Write a warm, professional DM the candidate can copy-paste on LinkedIn.\n"
                + "- Keep the body under 120 words.\n"
                + "- Start with \"Hi {name},\" using the recruiter's first name when known.\n"
                + "- Do not include email-style subject lines in the body.\n"
                + "- Do not include the LinkedIn post URL in the body (it will be sent separately).\n"
                + "- Mention relevant backend/Java/microservices experience from the candidate context.\n"
                + "- End with a short sign-off: Best regards, Ramij Amed Sardar.\n"
                + "- Do not invent unavailable facts.\n"
                + "- Mention Jadavpur University only if the post explicitly values Tier-1/premier institute background.\n"
                + "- Mention DSA/coding achievements only if the post explicitly asks for strong DSA/problem solving.\n"
                + "- Suggested recruiter first name from post author: " + recruiterName + "\n\n"
                + "Candidate context:\n" + candidateContext + "\n\n"
                + "Requirement signals:\n" + objectMapper.writeValueAsString(signals) + "\n\n"
                + "LinkedIn post JSON:\n" + objectMapper.writeValueAsString(post);
    }

    private String buildPrompt(PostData post, String recipientEmail, String candidateContext) throws IOException {
        JsonNode signals = objectMapper.createObjectNode()
                .put("tier1_requested", FallbackEmailGenerator.postRequiresTier1(post))
                .put("strong_dsa_requested", FallbackEmailGenerator.postRequiresStrongDsa(post));

        return "You are helping a candidate create a concise, recruiter-friendly application email.\n\n"
                + "Return only valid JSON with this exact schema:\n"
                + "{\n"
                + "  \"recipient_name\": \"string\",\n"
                + "  \"subject\": \"string\",\n"
                + "  \"body\": \"string\",\n"
                + "  \"post_summary\": \"string\"\n"
                + "}\n\n"
                + "Rules:\n"
                + "- Use a professional tone.\n"
                + "- Mention the candidate's relevant backend/distributed systems experience.\n"
                + "- Keep the body under 170 words and make it concise.\n"
                + "- Do not invent unavailable facts.\n"
                + "- If recruiter name is unknown, use \"there\".\n"
                + "- In the email body, the post reference must contain only the LinkedIn URL.\n"
                + "- Put the LinkedIn post reference at the very bottom of the email, after the signature.\n"
                + "- Mention Jadavpur University only if the post explicitly values Tier-1/top college/premier institute background.\n"
                + "- Mention DSA/coding achievements only if the post explicitly asks for strong DSA/problem solving/coding strength.\n\n"
                + "Candidate context:\n" + candidateContext + "\n\n"
                + "Requirement signals:\n" + objectMapper.writeValueAsString(signals) + "\n\n"
                + "LinkedIn post JSON:\n" + objectMapper.writeValueAsString(post) + "\n\n"
                + "Recipient email:\n" + recipientEmail;
    }

    private List<String> geminiKeys() {
        Set<String> keys = new LinkedHashSet<>();
        if (properties.getGeminiApiKey() != null && !properties.getGeminiApiKey().isEmpty()) {
            keys.add(properties.getGeminiApiKey());
        }
        keys.addAll(properties.getGeminiApiKeys());
        return new ArrayList<>(keys);
    }
}
