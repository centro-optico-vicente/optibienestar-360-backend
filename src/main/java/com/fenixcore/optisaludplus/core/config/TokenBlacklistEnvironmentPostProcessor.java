package com.fenixcore.optisaludplus.core.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * When {@code auth.token-blacklist.provider=memory}, opts out of all Redis machinery so
 * the backend can run without a Redis sidecar:
 *
 * <ul>
 *   <li>Excludes {@code RedisAutoConfiguration} and {@code RedisRepositoriesAutoConfiguration}
 *       — no connection factory, no template, no startup connect attempt.</li>
 *   <li>Disables the actuator Redis health indicator — otherwise readiness/liveness probes
 *       would report DOWN against a non-existent Redis.</li>
 * </ul>
 *
 * <p>Preserves any pre-existing {@code spring.autoconfigure.exclude} entries by appending
 * to them rather than replacing.
 */
public class TokenBlacklistEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROVIDER_KEY = "auth.token-blacklist.provider";
    private static final String MEMORY_MODE  = "memory";
    private static final String EXCLUDE_KEY  = "spring.autoconfigure.exclude";

    private static final Set<String> REDIS_AUTO_CONFIGS = Set.of(
            "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
            "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
            "org.springframework.boot.data.redis.autoconfigure.health.DataRedisHealthContributorAutoConfiguration"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String provider = environment.getProperty(PROVIDER_KEY, "redis");
        if (!MEMORY_MODE.equalsIgnoreCase(provider)) {
            return;
        }

        // Preserve any pre-existing exclusions (e.g. AwsAutoConfiguration in application.properties).
        Set<String> merged = new LinkedHashSet<>();
        String existing = environment.getProperty(EXCLUDE_KEY, "");
        if (!existing.isBlank()) {
            for (String entry : existing.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    merged.add(trimmed);
                }
            }
        }
        merged.addAll(REDIS_AUTO_CONFIGS);

        Map<String, Object> overrides = new HashMap<>();
        overrides.put(EXCLUDE_KEY, String.join(",", merged));
        overrides.put("management.health.redis.enabled", "false");

        environment.getPropertySources().addFirst(
                new MapPropertySource("tokenBlacklistMemoryModeOverrides", overrides));
    }

    @Override
    public int getOrder() {
        // Run after ConfigData (application.properties is loaded so we can read the provider key),
        // but before auto-configuration kicks in (so our excludes take effect).
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
