package com.jobemailer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

@Component
public class JsonFileStore {
    private final ObjectMapper objectMapper;

    public JsonFileStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> readMap(String path) {
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }

    public <T> T readValue(String path, TypeReference<T> typeReference, T defaultValue) {
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), typeReference);
        } catch (IOException e) {
            return defaultValue;
        }
    }

    public void writeValue(String path, Object value) throws IOException {
        Path file = Path.of(path);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardCharsets.UTF_8);
    }

    public void appendJsonLine(String path, Object value) throws IOException {
        Path file = Path.of(path);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(
                file,
                objectMapper.writeValueAsString(value) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE
        );
    }
}
