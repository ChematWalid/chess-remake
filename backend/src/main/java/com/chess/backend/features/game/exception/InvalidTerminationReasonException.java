package com.chess.backend.features.game.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class InvalidTerminationReasonException extends AppException {
    public InvalidTerminationReasonException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_TERMINATION_REASON", message);
    }
}
