package com.fenixcore.optibienestar360.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class WebUrlValidator implements ConstraintValidator<WebUrl, String> {

    private static final Pattern WEB_URL =
            Pattern.compile("^https?://[^\\s/?#]+\\.[^\\s/?#]+(?:[/?#]\\S*)?$", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return WEB_URL.matcher(value.strip()).matches();
    }
}
