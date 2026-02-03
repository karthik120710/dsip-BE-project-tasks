package com.dsip.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.lang.reflect.Method;

public class EnumValidator implements ConstraintValidator<ValidEnum, Integer> {
    private Class<? extends Enum<?>> enumClass;

    @Override
    public void initialize(ValidEnum constraintAnnotation) {
        this.enumClass = constraintAnnotation.enumClass();
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {

            Method getValueMethod = enumClass.getMethod("getValue");
            return Arrays.stream(enumClass.getEnumConstants())
                    .anyMatch(e -> {
                        try {
                            Integer enumValue = (Integer) getValueMethod.invoke(e);
                            return enumValue.equals(value);
                        } catch (Exception ex) {
                            return false;
                        }
                    });
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
