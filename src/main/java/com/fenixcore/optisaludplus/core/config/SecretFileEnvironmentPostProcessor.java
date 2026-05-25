package com.fenixcore.optisaludplus.core.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps Docker-style file-secret env vars (FOO_FILE=/path) to property FOO,
 * resolved as the trimmed UTF-8 contents of the file.
 *
 * <p>Lets {@code application*.properties} keep using {@code ${DATABASE_PASSWORD}} unchanged
 * while the compose file mounts secrets at {@code /run/secrets/<name>} and passes their paths
 * via {@code DATABASE_PASSWORD_FILE}, {@code JWT_SECRET_FILE}, etc.
 *
 * <p>If both {@code DATABASE_PASSWORD} and {@code DATABASE_PASSWORD_FILE} are set, the
 * existing {@code DATABASE_PASSWORD} wins — this processor never overwrites resolved properties.
 *
 * <p>Failures to read a file are silent on purpose: never log the path or content, both of
 * which can leak secret material into stack traces.
 */
public class SecretFileEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String SUFFIX = "_FILE";
    private static final String PROPERTY_SOURCE_NAME = "secretFiles";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> resolved = new HashMap<>();

        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source.getSource() instanceof Map<?, ?> map)) {
                continue;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!key.endsWith(SUFFIX) || key.length() == SUFFIX.length()) {
                    continue;
                }
                String baseKey = key.substring(0, key.length() - SUFFIX.length());
                if (resolved.containsKey(baseKey) || environment.containsProperty(baseKey)) {
                    continue;
                }
                Object rawPath = entry.getValue();
                if (rawPath == null) {
                    continue;
                }
                String pathValue = rawPath.toString().trim();
                if (pathValue.isEmpty()) {
                    continue;
                }
                try {
                    String content = Files.readString(Path.of(pathValue), StandardCharsets.UTF_8).trim();
                    resolved.put(baseKey, content);
                } catch (IOException ignored) {
                    // Silent: missing/unreadable file → leave property unresolved so existing
                    // defaults in application*.properties (or @Value defaults) apply.
                }
            }
        }

        if (!resolved.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, resolved));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
