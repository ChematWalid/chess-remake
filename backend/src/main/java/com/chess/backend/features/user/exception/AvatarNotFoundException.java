package com.chess.backend.features.user.exception;


import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class AvatarNotFoundException extends AppException {
    public AvatarNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "AVATAR_NOT_FOUND", message);
    }
}
