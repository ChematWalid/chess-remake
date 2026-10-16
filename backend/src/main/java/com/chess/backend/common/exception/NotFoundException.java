package com.chess.backend.common.exception;

import org.springframework.http.HttpStatus;

public final class NotFoundException extends AppException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
