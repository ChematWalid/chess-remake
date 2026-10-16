package com.chess.backend.features.game.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class InvalidGameResultException extends AppException {
    public InvalidGameResultException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_GAME_RESULT", message);
    }
}
