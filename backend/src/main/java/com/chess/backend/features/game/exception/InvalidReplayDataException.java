package com.chess.backend.features.game.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class InvalidReplayDataException extends AppException {
    public InvalidReplayDataException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_REPLAY_DATA", message);
    }
}
