package com.tripmind.tripmind.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path dotenvPath = Path.of(".env");
        if (!Files.isRegularFile(dotenvPath)) {
            return;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(dotenvPath)) {
                parseLine(line, values);
            }
        } catch (IOException ignored) {
            return;
        }

        if (!values.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource("tripmindDotenv", values));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private void parseLine(String rawLine, Map<String, Object> values) {
        String line = rawLine.trim();
        if (line.isBlank() || line.startsWith("#") || !line.contains("=")) {
            return;
        }

        String[] parts = line.split("=", 2);
        String key = parts[0].trim();
        String value = unquote(parts[1].trim());
        if (!key.isBlank()) {
            values.put(key, value);
            values.put(toSpringPropertyName(key), value);
        }
    }

    private String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String toSpringPropertyName(String key) {
        return key.toLowerCase().replace('_', '.');
    }
}
