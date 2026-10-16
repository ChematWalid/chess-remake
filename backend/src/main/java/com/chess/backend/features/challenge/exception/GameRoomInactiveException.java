package com.chess.backend.features.challenge.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class GameRoomInactiveException extends AppException {
    public GameRoomInactiveException(String message) {
        super(HttpStatus.CONFLICT, "GAME_ROOM_INACTIVE", message);
    }
}
