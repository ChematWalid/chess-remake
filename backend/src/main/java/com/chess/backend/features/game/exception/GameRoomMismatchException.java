package com.chess.backend.features.game.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class GameRoomMismatchException extends AppException {
    public GameRoomMismatchException(String message) {
        super(HttpStatus.CONFLICT, "GAME_ROOM_MISMATCH", message);
    }
}
