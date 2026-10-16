package com.chess.backend.common.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class UserNotFoundException extends AppException {
    public UserNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", message);
    }
}
