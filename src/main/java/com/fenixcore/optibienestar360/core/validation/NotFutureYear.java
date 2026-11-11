package com.fenixcore.optibienestar360.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A year that is not in the future (e.g. a foundation year: 2030 is rejected, backend#413). Accepts any integral
 * number type; null passes. Pair it with {@code @Min} for the lower bound.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotFutureYearValidator.class)
@Documented
public @interface NotFutureYear {

    String message() default "{validation.year.not_future}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
