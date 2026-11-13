package com.fenixcore.optibienestar360.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A phone number as people type it: digits with the usual separators ({@code 0414-1234567},
 * {@code +58 414 123 4567}, {@code (0251) 555.1234}) and 10 to 15 digits in total — a Venezuelan number has 11
 * (with the leading 0) or 12 (with +58). Rejects free text such as {@code "hola"} (backend#413). Same rule as the
 * frontend's {@code isValidPhone}.
 *
 * <p>Null / blank values pass — use {@code @NotBlank} if the field is required (blank also clears a field in the
 * PATCH-style profile edit).</p>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberValidator.class)
@Documented
public @interface PhoneNumber {

    String message() default "{validation.phone.format}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
