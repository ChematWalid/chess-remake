package com.chess.backend.features.challenge.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class ChallengeAlreadyPendingException extends AppException {
    public ChallengeAlreadyPendingException(String message) {
        super(HttpStatus.CONFLICT, "CHALLENGE_ALREADY_PENDING", message);
    }
}
