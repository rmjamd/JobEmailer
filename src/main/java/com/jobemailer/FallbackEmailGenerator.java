package com.jobemailer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FallbackEmailGenerator {
    private static final Pattern EMAIL_NAME_SPLIT = Pattern.compile("[._\\-]+");
    private static final Pattern HASHTAG = Pattern.compile("(?i)(?<!\\S)#[\\p{L}0-9_]+");
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "(?i)\\b((?:senior|staff|lead|principal|junior|sr\\.?|jr\\.?|associate)?\\s*"
                    + "(?:backend|back\\s*end|frontend|front\\s*end|full\\s*stack|software|java|platform|data|devops|"
                    + "site reliability|sre|qa|test|mobile|android|ios|ml|ai|cloud|security|python|node(?:\\.js)?|golang|react|"
                    + "spring(?:\\s*boot)?)?\\s*"
                    + "(?:engineer|developer|architect|manager|intern|consultant|specialist|analyst))\\b");
    private static final Pattern COMPANY_PATTERN = Pattern.compile(
            "(?i)\\b(?:we(?:'re| are)? hiring(?: at)?|join|opportunity at|opening at|role at|position at)\\s+"
                    + "([A-Z][A-Za-z0-9&.\\-]*(?:\\s+[A-Z][A-Za-z0-9&.\\-]*){0,4})\\b");
    private static final Pattern COMPANY_IS_HIRING_PATTERN = Pattern.compile(
            "\\b([A-Z][A-Za-z0-9&.\\-]*(?:\\s+[A-Z][A-Za-z0-9&.\\-]*){0,4})\\s+is hiring\\b");
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "(?i)\\b(?:in|location|based in)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,2})\\b");

    private FallbackEmailGenerator() {
    }

    public static EmailDraft generateLinkedInDm(PostData post, JobEmailerProperties properties) {
        EmailDraft draft = new EmailDraft();
        String recipientName = inferRecruiterNameFromPost(post);
        String requirementHighlights = buildRequirementHighlights(post);
        String body = "Hi " + recipientName + ",\n\n"
                + buildRelevanceSentence(post) + " "
                + "I have " + properties.getYearsOfExperience()
                + " years of experience building scalable backend systems using Java, Spring Boot, Kafka, Redis, and cloud-native infrastructure."
                + (requirementHighlights.isEmpty() ? "" : " " + requirementHighlights)
                + " I would love to connect and share my resume if my profile looks relevant.\n\n"
                + "Best regards,\n"
                + "Ramij Amed Sardar";

        draft.setRecipientName(recipientName);
        draft.setSubject("");
        draft.setBody(body);
        draft.setPostSummary(post.getTitle() != null && !post.getTitle().isEmpty() ? post.getTitle() : truncate(post.getContent(), 180));
        return draft;
    }

    public static String inferRecruiterNameFromPost(PostData post) {
        String author = safe(post.getAuthor());
        if (!author.isEmpty()) {
            String[] parts = author.trim().split("\\s+");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                return parts[0].substring(0, 1).toUpperCase(Locale.ROOT) + parts[0].substring(1).toLowerCase(Locale.ROOT);
            }
        }
        return "there";
    }

    public static EmailDraft generate(PostData post, String recipientEmail, JobEmailerProperties properties) {
        EmailDraft draft = new EmailDraft();
        String recipientName = inferNameFromEmail(recipientEmail);
        String company = inferCompanyFromPost(post);
        String subject = company.isEmpty()
                ? "Application for Backend Engineer opportunity"
                : "Application for Backend Engineer opportunities at " + company;
        String requirementHighlights = buildRequirementHighlights(post);
        String body = "Hi " + recipientName + ",\n\n"
                + buildRelevanceSentence(post) + " "
                + "I have " + properties.getYearsOfExperience()
                + " years of experience building scalable backend systems using Java, Spring Boot, Kafka, Redis, and cloud-native infrastructure."
                + (requirementHighlights.isEmpty() ? "" : " " + requirementHighlights)
                + "\n\nI have attached my resume for quick reference and would be glad to discuss further if my profile looks relevant.\n\n"
                + "Best regards,\n"
                + "Ramij Amed Sardar\n"
                + "Phone: +91 6289730218\n"
                + "Email: ramijnalpur@gmail.com\n"
                + "LinkedIn: https://www.linkedin.com/in/ramij-amed-sardar-0112071b9/\n\n"
                + "Post reference:\n" + post.getUrl();

        draft.setRecipientName(recipientName);
        draft.setSubject(subject);
        draft.setBody(body);
        draft.setPostSummary(post.getTitle() != null && !post.getTitle().isEmpty() ? post.getTitle() : truncate(post.getContent(), 180));
        return draft;
    }

    public static boolean postRequiresTier1(PostData post) {
        String text = combinedText(post);
        return containsAny(text, "tier 1", "tier-1", "top college", "top-tier college", "premier institute", "top university");
    }

    public static boolean postRequiresStrongDsa(PostData post) {
        String text = combinedText(post);
        return containsAny(text, "dsa", "data structures", "algorithms", "problem solving", "problem-solving", "leetcode", "competitive programming", "strong coding");
    }

    private static String buildRequirementHighlights(PostData post) {
        List<String> highlights = new ArrayList<>();
        if (postRequiresTier1(post)) {
            highlights.add("I graduated from Jadavpur University, a Tier-1 engineering institution.");
        }
        if (postRequiresStrongDsa(post)) {
            highlights.add("I have solved 670+ LeetCode problems and 1350+ problems overall across coding platforms.");
        }
        return String.join(" ", highlights);
    }

    private static String buildRelevanceSentence(PostData post) {
        String role = inferRoleFromPost(post);
        String company = inferCompanyFromPost(post);
        String location = inferLocationFromPost(post);
        List<String> bits = new ArrayList<>();
        if (!role.isEmpty()) bits.add(role);
        if (!company.isEmpty()) bits.add("at " + company);
        if (!location.isEmpty()) bits.add("in " + location);
        String sentence = bits.isEmpty()
                ? "I came across your LinkedIn post and wanted to reach out regarding relevant engineering opportunities."
                : "I came across your LinkedIn post for " + String.join(" ", bits) + ".";
        String text = combinedText(post);
        if (containsAny(text, "java", "kotlin")) {
            sentence += " My background is strongest in Java-based backend and distributed systems engineering.";
        } else {
            sentence += " My background is strongest in backend and distributed systems engineering.";
        }
        return sentence;
    }

    private static String inferCompanyFromPost(PostData post) {
        String content = sanitizePostText(post.getContent());
        Matcher matcher = Pattern.compile("\\bwe at ([A-Z][A-Za-z0-9&.\\- ]+?) are hiring\\b", Pattern.CASE_INSENSITIVE).matcher(content);
        if (matcher.find()) return sanitizeCompanyCandidate(matcher.group(1));
        matcher = COMPANY_PATTERN.matcher(content);
        if (matcher.find()) return sanitizeCompanyCandidate(matcher.group(1));
        matcher = COMPANY_IS_HIRING_PATTERN.matcher(content);
        if (matcher.find()) return sanitizeCompanyCandidate(matcher.group(1));

        String title = sanitizePostText(post.getTitle());
        if (title.contains("|")) {
            String company = sanitizeCompanyCandidate(title.split("\\|", 2)[0]);
            if (!company.isEmpty()) {
                return company;
            }
        }
        return "";
    }

    private static String inferRoleFromPost(PostData post) {
        String text = sanitizePostText(post.getContent()) + "\n" + sanitizePostText(post.getTitle());
        Matcher matcher = TITLE_PATTERN.matcher(text);
        while (matcher.find()) {
            String title = sanitizeRoleCandidate(matcher.group(1));
            if (!title.isEmpty()) {
                return title;
            }
        }
        return "";
    }

    private static String inferLocationFromPost(PostData post) {
        Matcher matcher = LOCATION_PATTERN.matcher(sanitizePostText(post.getContent()));
        while (matcher.find()) {
            String location = sanitizeLocationCandidate(matcher.group(1));
            if (!location.isEmpty()) {
                return location;
            }
        }
        return "";
    }

    private static String inferNameFromEmail(String email) {
        String local = email.split("@", 2)[0];
        String[] pieces = EMAIL_NAME_SPLIT.split(local);
        for (String piece : pieces) {
            if (!piece.isEmpty() && !piece.chars().allMatch(Character::isDigit)) {
                return piece.substring(0, 1).toUpperCase(Locale.ROOT) + piece.substring(1).toLowerCase(Locale.ROOT);
            }
        }
        return "there";
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    private static String combinedText(PostData post) {
        return (sanitizePostText(post.getContent()) + "\n" + sanitizePostText(post.getTitle())).toLowerCase(Locale.ROOT);
    }

    private static String normalizeSpace(String value) {
        return safe(value).replaceAll("\\s+", " ").trim();
    }

    public static String sanitizePostText(String value) {
        String text = safe(value)
                .replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\\t", " ");
        text = HASHTAG.matcher(text).replaceAll(" ");
        text = text.replaceAll("(?i)\\bon linkedin\\b", " ");
        text = text.replace('|', ' ');
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private static String sanitizeRoleCandidate(String value) {
        String role = normalizeSpace(value)
                .replaceAll("(?i)^hiring\\s+", "")
                .replaceAll("[,;:]+$", "");
        String lower = role.toLowerCase(Locale.ROOT);
        if (role.isEmpty() || role.contains("#")) {
            return "";
        }
        if (containsAny(lower, "alert", "opportunity", "opportunities", "opening", "openings", "hiring")) {
            return "";
        }
        return role;
    }

    private static String sanitizeCompanyCandidate(String value) {
        String company = normalizeSpace(value)
                .replaceAll("[,;:]+$", "");
        String lower = company.toLowerCase(Locale.ROOT);
        if (company.isEmpty() || company.contains("#")) {
            return "";
        }
        if (containsAny(lower, "hiring", "linkedin", "java", "spring", "kafka", "react", "microservices", "node")) {
            return "";
        }
        if (company.split("\\s+").length > 5) {
            return "";
        }
        return company;
    }

    private static String sanitizeLocationCandidate(String value) {
        String location = normalizeSpace(value)
                .replaceAll("[,;:]+$", "");
        String lower = location.toLowerCase(Locale.ROOT);
        if (location.isEmpty()) {
            return "";
        }
        if (containsAny(lower, "java", "node", "node.js", "react", "spring", "kafka", "aws", "graphql", "microservices")) {
            return "";
        }
        return location;
    }

    private static String truncate(String value, int limit) {
        String text = safe(value);
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
