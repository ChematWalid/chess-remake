package com.chess.backend.features.challenge.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class ChallengeRequiresFriendException extends AppException {
    public ChallengeRequiresFriendException(String message) {
        super(HttpStatus.FORBIDDEN, "CHALLENGE_REQUIRES_FRIENDSHIP", message);
    }
}
