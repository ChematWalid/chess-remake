package com.chess.backend.exception;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    public AppException(HttpStatus status, String errorCode, String message) { super(message); this.status = status; this.errorCode = errorCode; }
    public HttpStatus status() { return status; }
    public String errorCode() { return errorCode; }
}
