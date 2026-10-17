package com.chess.backend.features.challenge.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class InvalidChallengePositionException extends AppException {
    public InvalidChallengePositionException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_CHALLENGE_POSITION", message);
    }
}
