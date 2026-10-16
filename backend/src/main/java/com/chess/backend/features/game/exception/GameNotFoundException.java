package com.chess.backend.features.game.exception;


import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class GameNotFoundException extends AppException {
    public GameNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "GAME_NOT_FOUND", message);
    }
}
