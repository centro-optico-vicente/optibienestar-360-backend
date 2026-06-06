package com.fenixcore.optisaludplus.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class VenezuelanDocumentNumberValidator implements ConstraintValidator<VenezuelanDocumentNumber, String> {

    /** 1 to 9 digits, no other characters. Leading zeros tolerated for legacy data. */
    private static final Pattern PATTERN = Pattern.compile("^[0-9]{1,9}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            // Null / empty handled by @NotBlank if presence is required.
            return true;
        }
        return PATTERN.matcher(value).matches();
    }
}
