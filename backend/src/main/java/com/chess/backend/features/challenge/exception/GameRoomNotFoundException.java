package com.chess.backend.features.challenge.exception;

import com.chess.backend.common.exception.NotFoundException;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class GameRoomNotFoundException extends AppException {
    public GameRoomNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "GAME_ROOM_NOT_FOUND", message);
    }
}
