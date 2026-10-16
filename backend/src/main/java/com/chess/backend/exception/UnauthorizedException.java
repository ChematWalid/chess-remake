package com.chess.backend.exception;

import org.springframework.http.HttpStatus;

public final class UnauthorizedException extends AppException {
    public UnauthorizedException(String message) { super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message); }
}
