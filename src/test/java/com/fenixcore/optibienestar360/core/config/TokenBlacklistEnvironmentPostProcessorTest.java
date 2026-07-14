package com.fenixcore.optibienestar360.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBlacklistEnvironmentPostProcessorTest {

    private final TokenBlacklistEnvironmentPostProcessor processor =
            new TokenBlacklistEnvironmentPostProcessor();

    @Test
    void redisModeMakesNoChanges() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("auth.token-blacklist.provider", "redis");
        env.setProperty("spring.autoconfigure.exclude", "com.example.Foo");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.autoconfigure.exclude")).isEqualTo("com.example.Foo");
        assertThat(env.getProperty("management.health.redis.enabled")).isNull();
    }

    @Test
    void unsetProviderDefaultsToRedisAndMakesNoChanges() {
        MockEnvironment env = new MockEnvironment();

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.autoconfigure.exclude")).isNull();
        assertThat(env.getProperty("management.health.redis.enabled")).isNull();
    }

    @Test
    void memoryModeAppendsRedisExcludesAndDisablesHealth() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("auth.token-blacklist.provider", "memory");

        processor.postProcessEnvironment(env, new SpringApplication());

        String excludes = env.getProperty("spring.autoconfigure.exclude");
        assertThat(excludes)
                .contains("DataRedisAutoConfiguration")
                .contains("DataRedisRepositoriesAutoConfiguration")
                .contains("DataRedisHealthContributorAutoConfiguration");
        assertThat(env.getProperty("management.health.redis.enabled")).isEqualTo("false");
    }

    @Test
    void memoryModePreservesPreExistingExclusions() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("auth.token-blacklist.provider", "memory");
        env.setProperty("spring.autoconfigure.exclude",
                "io.awspring.cloud.autoconfigure.core.AwsAutoConfiguration");

        processor.postProcessEnvironment(env, new SpringApplication());

        String excludes = env.getProperty("spring.autoconfigure.exclude");
        assertThat(excludes)
                .contains("AwsAutoConfiguration")
                .contains("DataRedisAutoConfiguration");
    }

    @Test
    void memoryModeIsCaseInsensitive() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("auth.token-blacklist.provider", "MEMORY");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("management.health.redis.enabled")).isEqualTo("false");
    }
}
