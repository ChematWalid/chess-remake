package com.chess.backend.features.user.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class InvalidUserDraftException extends AppException {
    public InvalidUserDraftException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_USER_DRAFT", message);
    }
}
