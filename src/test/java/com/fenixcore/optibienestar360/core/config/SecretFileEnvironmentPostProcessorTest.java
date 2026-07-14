package com.fenixcore.optibienestar360.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecretFileEnvironmentPostProcessorTest {

    private final SecretFileEnvironmentPostProcessor processor = new SecretFileEnvironmentPostProcessor();

    @Test
    void readsFileContentsAndExposesAsBaseProperty(@TempDir Path tmp) throws IOException {
        Path secret = tmp.resolve("db_password");
        Files.writeString(secret, "s3cr3t-value\n");

        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "DATABASE_PASSWORD_FILE", secret.toString()
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("DATABASE_PASSWORD")).isEqualTo("s3cr3t-value");
    }

    @Test
    void doesNotOverwriteExistingProperty(@TempDir Path tmp) throws IOException {
        Path secret = tmp.resolve("db_password");
        Files.writeString(secret, "from-file");

        MockEnvironment env = new MockEnvironment();
        Map<String, Object> source = new HashMap<>();
        source.put("DATABASE_PASSWORD_FILE", secret.toString());
        source.put("DATABASE_PASSWORD", "from-env");
        env.getPropertySources().addFirst(new MapPropertySource("test", source));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("DATABASE_PASSWORD")).isEqualTo("from-env");
    }

    @Test
    void ignoresMissingFileSilently() {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "DATABASE_PASSWORD_FILE", "/nonexistent/path/that/does/not/exist"
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("DATABASE_PASSWORD")).isNull();
    }

    @Test
    void trimsTrailingWhitespaceFromFileContent(@TempDir Path tmp) throws IOException {
        Path secret = tmp.resolve("jwt_secret");
        Files.writeString(secret, "  token-value  \n\n");

        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "JWT_SECRET_FILE", secret.toString()
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("JWT_SECRET")).isEqualTo("token-value");
    }

    @Test
    void ignoresKeysWithoutFileSuffix() {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "DATABASE_PASSWORD", "not-a-file"
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("DATABASE_PASSWORD")).isEqualTo("not-a-file");
    }

    @Test
    void ignoresBlankFilePath(@TempDir Path tmp) {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "DATABASE_PASSWORD_FILE", "   "
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("DATABASE_PASSWORD")).isNull();
    }
}
