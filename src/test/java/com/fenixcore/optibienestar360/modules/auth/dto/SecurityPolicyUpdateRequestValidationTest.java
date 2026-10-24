package com.fenixcore.optibienestar360.modules.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation bounds of {@link SecurityPolicyUpdateRequest}, driven
 * through the standard Jakarta {@link Validator} factory. The same bounds
 * are enforced by CHECK constraints on {@code security_policies} (V191).
 */
class SecurityPolicyUpdateRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> invalidFields(SecurityPolicyUpdateRequest req) {
        Set<ConstraintViolation<SecurityPolicyUpdateRequest>> violations = validator.validate(req);
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void acceptsTheSeededValues() {
        assertThat(invalidFields(new SecurityPolicyUpdateRequest(5, 30, 90, 5, 3))).isEmpty();
    }

    @Test
    void acceptsZeroForPasswordHistoryAndExpiry() {
        assertThat(invalidFields(new SecurityPolicyUpdateRequest(1, 1, 0, 0, 1))).isEmpty();
    }

    @Test
    void rejectsNegativePasswordHistoryAndExpiry() {
        assertThat(invalidFields(new SecurityPolicyUpdateRequest(5, 30, -1, -1, 3)))
                .containsExactlyInAnyOrder("daysPasswordExpires", "passwordHistoryCount");
    }

    @Test
    void rejectsZeroWhereZeroMakesNoSense() {
        assertThat(invalidFields(new SecurityPolicyUpdateRequest(0, 0, 90, 5, 0)))
                .containsExactlyInAnyOrder("maxLoginAttempts", "lockoutDurationMinutes", "maxConcurrentSessions");
    }

    @Test
    void rejectsMissingFields() {
        assertThat(invalidFields(new SecurityPolicyUpdateRequest(null, null, null, null, null)))
                .containsExactlyInAnyOrder("maxLoginAttempts", "lockoutDurationMinutes",
                        "daysPasswordExpires", "passwordHistoryCount", "maxConcurrentSessions");
    }
}
