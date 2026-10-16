package com.chess.backend.features.game.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class InvalidWinnerException extends AppException {
    public InvalidWinnerException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_GAME_WINNER", message);
    }
}
