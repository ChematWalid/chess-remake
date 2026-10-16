package com.chess.backend.features.auth.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class RefreshTokenInvalidException extends AppException {
    public RefreshTokenInvalidException(String message) {
        super(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", message);
    }
}
