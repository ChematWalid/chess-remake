package com.chess.backend.features.challenge.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class ChallengeUnavailableException extends AppException {
    public ChallengeUnavailableException(String message) {
        super(HttpStatus.FORBIDDEN, "CHALLENGE_UNAVAILABLE", message);
    }
}
