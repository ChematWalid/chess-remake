package com.chess.backend.features.challenge.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class SelfChallengeException extends AppException {
    public SelfChallengeException(String message) {
        super(HttpStatus.BAD_REQUEST, "SELF_CHALLENGE", message);
    }
}
