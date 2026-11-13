package com.fenixcore.optibienestar360.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    private static final Pattern ALLOWED = Pattern.compile("^\\+?[\\d\\s().-]+$");
    static final int MIN_DIGITS = 10;
    static final int MAX_DIGITS = 15;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.strip();
        if (!ALLOWED.matcher(trimmed).matches()) {
            return false;
        }
        long digits = trimmed.chars().filter(Character::isDigit).count();
        return digits >= MIN_DIGITS && digits <= MAX_DIGITS;
    }
}
