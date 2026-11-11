package com.fenixcore.optibienestar360.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A web address with an {@code http}/{@code https} scheme and a dotted host ({@code https://miempresa.com},
 * {@code http://www.sitio.com.ve/ruta}). Rejects free text such as {@code "mi sitio web"} (backend#413), which
 * would otherwise be published as a broken link in the directory. Same rule as the frontend's {@code isValidWebsite}
 * (the frontend adds the missing {@code https://} before sending).
 *
 * <p>Null / blank values pass — blank clears the field in the PATCH-style profile edit.</p>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WebUrlValidator.class)
@Documented
public @interface WebUrl {

    String message() default "{validation.web_url.format}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
