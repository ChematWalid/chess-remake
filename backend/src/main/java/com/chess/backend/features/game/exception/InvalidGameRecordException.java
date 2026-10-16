package com.chess.backend.features.game.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class InvalidGameRecordException extends AppException {
    public InvalidGameRecordException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_GAME_RECORD", message);
    }
}
