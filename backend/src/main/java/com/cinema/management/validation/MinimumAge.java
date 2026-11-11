package com.cinema.management.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = MinimumAgeValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MinimumAge {
    String message() default "Tuổi chưa đạt yêu cầu";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    int value();
}

