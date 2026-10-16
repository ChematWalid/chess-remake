package com.chess.backend.features.user.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class UsernameAlreadyTakenException extends AppException {
    public UsernameAlreadyTakenException(String message) {
        super(HttpStatus.CONFLICT, "USERNAME_ALREADY_TAKEN", message);
    }
}
