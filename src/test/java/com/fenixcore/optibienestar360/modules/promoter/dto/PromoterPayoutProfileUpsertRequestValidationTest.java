package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Bean-validation of the payout account number (V182): empty or exactly 20 digits. */
class PromoterPayoutProfileUpsertRequestValidationTest {

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

    @Test
    void accepts20Digits_blank_andNull() {
        assertThat(validate("01020123450000012345")).isEmpty();
        assertThat(validate("")).isEmpty();
        assertThat(validate(null)).isEmpty();
    }

    @Test
    void rejectsWrongLengthOrNonDigits() {
        assertThat(validate("0102012345000001234")).hasSize(1);    // 19
        assertThat(validate("010201234500000123456")).hasSize(1);  // 21
        assertThat(validate("0102-0123-4500-00012")).hasSize(1);   // separators
    }

    private static Set<ConstraintViolation<PromoterPayoutProfileUpsertRequest>> validate(String accountNumber) {
        return validator.validate(new PromoterPayoutProfileUpsertRequest(
                null, accountNumber, null, null, null, null, null, null));
    }
}
