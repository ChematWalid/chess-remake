package com.chess.backend.features.challenge.exception;

import com.chess.backend.common.exception.NotFoundException;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class ChallengeNotFoundException extends AppException {
    public ChallengeNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "CHALLENGE_NOT_FOUND", message);
    }
}
