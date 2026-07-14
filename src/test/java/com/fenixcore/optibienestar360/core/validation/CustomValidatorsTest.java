package com.fenixcore.optibienestar360.core.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VenezuelanDocumentNumber} and {@link MinimumAge}
 * driven through the standard Jakarta {@link Validator} factory — exercises
 * the annotation discovery + validator class wiring + interpolated message,
 * not just the bare validator class.
 */
class CustomValidatorsTest {

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

    // ─── VenezuelanDocumentNumber ───────────────────────────────────────────

    @Test
    void documentNumber_acceptsDigitsOnly() {
        assertThat(validate(new TestObj("12345678", null))).isEmpty();
        assertThat(validate(new TestObj("1", null))).isEmpty();          // seed System user
        assertThat(validate(new TestObj("123456789", null))).isEmpty();  // 9-digit upper bound
    }

    @Test
    void documentNumber_acceptsNullAndEmpty() {
        // Presence is enforced by @NotBlank where required; this constraint
        // only checks format.
        assertThat(validate(new TestObj(null, null))).isEmpty();
        assertThat(validate(new TestObj("", null))).isEmpty();
    }

    @Test
    void documentNumber_rejectsLetters() {
        Set<ConstraintViolation<TestObj>> v = validate(new TestObj("V12345678", null));
        assertThat(v).hasSize(1);
        // Assert the message TEMPLATE (locale-agnostic) instead of the
        // interpolated text — the test runner's locale would otherwise
        // make this flaky between English and Spanish dev machines.
        assertThat(v.iterator().next().getMessageTemplate())
                .isEqualTo("{validation.document_number.venezuelan_format}");
    }

    @Test
    void documentNumber_rejectsTooLong() {
        // 10 digits — one past the upper bound
        assertThat(validate(new TestObj("1234567890", null))).hasSize(1);
    }

    @Test
    void documentNumber_rejectsDashesAndSpaces() {
        assertThat(validate(new TestObj("12 345 678", null))).hasSize(1);
        assertThat(validate(new TestObj("12-345-678", null))).hasSize(1);
    }

    // ─── MinimumAge ─────────────────────────────────────────────────────────

    @Test
    void minimumAge_acceptsAdult() {
        LocalDate adult = LocalDate.now().minusYears(25);
        assertThat(validate(new TestObj(null, adult))).isEmpty();
    }

    @Test
    void minimumAge_acceptsExactlyEighteen() {
        // Exactly the cutoff — must pass (>=, not >).
        LocalDate exactly18 = LocalDate.now().minusYears(18);
        assertThat(validate(new TestObj(null, exactly18))).isEmpty();
    }

    @Test
    void minimumAge_rejectsMinor() {
        LocalDate minor = LocalDate.now().minusYears(17);
        Set<ConstraintViolation<TestObj>> v = validate(new TestObj(null, minor));
        assertThat(v).hasSize(1);
        // Template assertion — see documentNumber_rejectsLetters for rationale.
        assertThat(v.iterator().next().getMessageTemplate())
                .isEqualTo("{validation.age.minimum}");
    }

    @Test
    void minimumAge_rejectsFutureBirthDate() {
        // Future date → negative age → fails >= minimumAge
        LocalDate future = LocalDate.now().plusYears(5);
        assertThat(validate(new TestObj(null, future))).hasSize(1);
    }

    @Test
    void minimumAge_acceptsNull() {
        // Presence enforced by @NotNull where required.
        assertThat(validate(new TestObj(null, null))).isEmpty();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Set<ConstraintViolation<TestObj>> validate(TestObj obj) {
        return validator.validate(obj);
    }

    /**
     * Test record carrying both constraints. Using fields rather than method
     * targets to mirror how the annotations land on real record DTOs.
     */
    record TestObj(
            @VenezuelanDocumentNumber String documentNumber,
            @MinimumAge LocalDate birthDate
    ) {}
}
