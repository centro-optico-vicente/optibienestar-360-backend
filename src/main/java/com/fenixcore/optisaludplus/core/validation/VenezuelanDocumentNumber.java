package com.fenixcore.optisaludplus.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a Venezuelan cédula <em>number</em> portion (the digits part
 * only). The {@code documentType} prefix (V / E) is validated separately
 * with {@code @Pattern("^[VE]$")} on its own field.
 *
 * <p>Format: 1 to 9 digits. Seed users carry {@code "1"} and {@code "2"}
 * (System and Administrador) so the lower bound stays at 1 — extending the
 * pattern to require 6+ digits would break the seed migrations and any
 * dev-only test data. Real Venezuelan cédulas range 6-9 digits in practice
 * but the format itself doesn't reject shorter values; the application
 * layer is the right place to add minimum-length business rules per
 * surface.</p>
 *
 * <p>Null / empty values pass through this constraint — use {@code @NotBlank}
 * if the field is required.</p>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = VenezuelanDocumentNumberValidator.class)
@Documented
public @interface VenezuelanDocumentNumber {

    String message() default "{validation.document_number.venezuelan_format}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
