package com.chess.backend.features.auth.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class EmailActionTokenInvalidException extends AppException {
    public EmailActionTokenInvalidException(String message) {
        super(HttpStatus.BAD_REQUEST, "EMAIL_ACTION_TOKEN_INVALID", message);
    }
}
