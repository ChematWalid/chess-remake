package com.chess.backend.features.user.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class UserSettingsNotFoundException extends AppException {
    public UserSettingsNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "USER_SETTINGS_NOT_FOUND", message);
    }
}
