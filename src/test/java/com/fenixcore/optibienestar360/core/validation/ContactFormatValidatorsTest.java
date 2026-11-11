package com.fenixcore.optibienestar360.core.validation;

import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyProfileUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Year;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Format checks of the ally self-registration and profile contact data (backend#413). */
class ContactFormatValidatorsTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private final PhoneNumberValidator phone = new PhoneNumberValidator();
    private final WebUrlValidator web = new WebUrlValidator();
    private final NotFutureYearValidator year = new NotFutureYearValidator();

    @ParameterizedTest
    @ValueSource(strings = {"0414-1234567", "04141234567", "+58 414 123 4567", "(0251) 555.1234", " 0412 1234567 "})
    void phone_acceptsTheUsualFormats(String value) {
        assertThat(phone.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hola", "abc0414-1234567", "555-1234", "0414-12345678901234", "0414_1234567"})
    void phone_rejectsFreeTextAndWrongLengths(String value) {
        assertThat(phone.isValid(value, null)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void phone_letsBlankThrough_requiredIsNotBlanksJob(String value) {
        assertThat(phone.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://miempresa.com", "http://www.sitio.com.ve/ruta?x=1", "HTTPS://Optica.COM", "https://site.com:8080"})
    void webUrl_acceptsAddressesWithSchemeAndDottedHost(String value) {
        assertThat(web.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"mi sitio web", "miempresa.com", "https://localhost", "ftp://files.com", "https://mi empresa.com"})
    void webUrl_rejectsFreeTextAndAddressesWithoutSchemeOrDomain(String value) {
        assertThat(web.isValid(value, null)).isFalse();
    }

    @Test
    void notFutureYear_acceptsTheCurrentYearAndRejectsNextOne() {
        int current = Year.now().getValue();
        assertThat(year.isValid((short) current, null)).isTrue();
        assertThat(year.isValid((short) 1990, null)).isTrue();
        assertThat(year.isValid(null, null)).isTrue();
        assertThat(year.isValid((short) (current + 1), null)).isFalse();
    }

    @Test
    void profileUpdate_flagsThePhoneWebsiteAndYearFields() {
        MyAllyProfileUpdateRequest request = new MyAllyProfileUpdateRequest(
                null, null, "hola", "0414-1234567", "mi sitio web", null, null, null, null, null, null,
                null, null, (short) (Year.now().getValue() + 4), null, null, null, null,
                null, null, "abc", null,
                null, null, null, null);

        Set<ConstraintViolation<MyAllyProfileUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("phone", "website", "foundedYear", "appointmentPhone");
    }
}
