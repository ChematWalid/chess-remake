package com.chess.backend.features.user.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class AvatarReadException extends AppException {
    public AvatarReadException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "AVATAR_READ_FAILED", message);
    }
}
