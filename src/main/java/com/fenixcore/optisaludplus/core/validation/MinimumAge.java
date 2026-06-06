package com.fenixcore.optisaludplus.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a {@link java.time.LocalDate} representing a birth date is
 * at least {@link #value()} years before today. Default {@code 18} matches
 * the program's minimum-age rule for Member titulares (afiliados) — by
 * contrast, {@link com.fenixcore.optisaludplus.modules.member.entity.Beneficiary
 * Beneficiary} rows can have any age including minors, so the constraint
 * goes on the titular's request DTO, not on the Person itself.
 *
 * <p>Null values pass through — use {@code @NotNull} if the field is
 * required. Future-dated birth dates are also rejected (treated as age
 * less than the minimum).</p>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MinimumAgeValidator.class)
@Documented
public @interface MinimumAge {

    int value() default 18;

    String message() default "{validation.age.minimum}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
