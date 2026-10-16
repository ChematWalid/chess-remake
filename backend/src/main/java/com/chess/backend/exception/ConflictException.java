package com.chess.backend.exception;

import org.springframework.http.HttpStatus;

public final class ConflictException extends AppException {
    public ConflictException(String message) { super(HttpStatus.CONFLICT, "CONFLICT", message); }
}
