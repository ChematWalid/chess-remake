package com.chess.backend.common.exception;

import org.springframework.http.HttpStatus;

public final class InvalidPaginationException extends AppException {
    public InvalidPaginationException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", message);
    }
}
