package com.cinema.management.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Clock;
import java.time.LocalDate;

public class MinimumAgeValidator implements ConstraintValidator<MinimumAge, LocalDate> {
    private int minimumAge;

    @Override
    public void initialize(MinimumAge annotation) {
        minimumAge = annotation.value();
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        return value == null || !value.plusYears(minimumAge).isAfter(LocalDate.now(Clock.systemUTC()));
    }
}

