package com.fenixcore.optisaludplus.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.Period;

public class MinimumAgeValidator implements ConstraintValidator<MinimumAge, LocalDate> {

    private int minimumAge;

    @Override
    public void initialize(MinimumAge constraintAnnotation) {
        this.minimumAge = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(LocalDate birthDate, ConstraintValidatorContext context) {
        if (birthDate == null) {
            // Null handled by @NotNull if the field is required.
            return true;
        }
        // Future-dated birth dates produce a negative Period.years and fail
        // the minimumAge check, which is the desired behaviour — no need to
        // special-case "birth date in the future" with a different message.
        return Period.between(birthDate, LocalDate.now()).getYears() >= minimumAge;
    }
}
