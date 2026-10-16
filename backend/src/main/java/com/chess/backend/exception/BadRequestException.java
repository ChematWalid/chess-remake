package com.chess.backend.exception;

import org.springframework.http.HttpStatus;

public final class BadRequestException extends AppException {
    public BadRequestException(String message) { super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message); }
}
