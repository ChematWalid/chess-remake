package com.chess.backend.features.challenge.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class InvalidTimeControlException extends AppException {
    public InvalidTimeControlException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_TIME_CONTROL", message);
    }
}
