package com.jobemailer;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class ResourcePathResolver {
    private final ResourceLoader resourceLoader;

    public ResourcePathResolver(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public void requireExists(String location, String label) {
        Resource resource = getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException(label + " not found: " + location);
        }
    }

    public Path materializeToPath(String location, String label) throws IOException {
        Resource resource = getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException(label + " not found: " + location);
        }
        if (resource.isFile()) {
            return resource.getFile().toPath();
        }

        String filename = resource.getFilename();
        String suffix = ".tmp";
        if (filename != null && filename.contains(".")) {
            suffix = filename.substring(filename.lastIndexOf('.'));
        }

        Path tempFile = Files.createTempFile("jobemailer-resource-", suffix);
        try (InputStream inputStream = resource.getInputStream()) {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    private Resource getResource(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("Resource location is required");
        }
        if (location.startsWith("classpath:") || location.startsWith("file:")) {
            return resourceLoader.getResource(location);
        }
        return resourceLoader.getResource("file:" + location);
    }
}
