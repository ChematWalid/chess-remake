package com.chess.backend.exception;

import org.springframework.http.HttpStatus;

public final class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String message) { super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message); }
}
