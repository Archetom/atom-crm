package io.github.archetom.crm.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Validates a nullable request string after trim using Unicode code-point length. */
@Documented
@Constraint(validatedBy = TrimmedCodePointSizeValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TrimmedCodePointSize {

    String message() default "must not exceed {max} characters after trimming";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int max();
}
