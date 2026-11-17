package com.launchmate.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Loads versioned system prompts from classpath:prompts/{version}/{name}.md.
 * Prompts are read once and kept in memory, so the text (and therefore the cached prefix)
 * is byte-identical on every request.
 */
@Service
public class PromptTemplateService {

    public static final String CURRENT_VERSION = "v1";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String name) {
        return cache.computeIfAbsent(name, this::read);
    }

    private String read(String name) {
        String path = "prompts/" + CURRENT_VERSION + "/" + name + ".md";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Prompt template not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read prompt template: " + path, e);
        }
    }
}
