package com.chess.backend.features.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class UsernameValidator implements ConstraintValidator<ValidUsername, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        String username = value.trim();
        if (username.length() < 3 || username.length() > 30) return false;
        for (int index = 0; index < username.length(); index++) {
            char character = username.charAt(index);
            if (!(character >= 'a' && character <= 'z') && !(character >= 'A' && character <= 'Z')
                    && !(character >= '0' && character <= '9') && character != '_') return false;
        }
        return true;
    }
}
