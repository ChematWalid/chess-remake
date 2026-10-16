package com.chess.backend.features.game.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class InvalidPlayerIdException extends AppException {
    public InvalidPlayerIdException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PLAYER_ID", message);
    }
}
