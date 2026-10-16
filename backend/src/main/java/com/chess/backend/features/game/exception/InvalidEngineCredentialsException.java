package com.chess.backend.features.game.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class InvalidEngineCredentialsException extends AppException {
    public InvalidEngineCredentialsException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_ENGINE_CREDENTIALS", message);
    }
}
