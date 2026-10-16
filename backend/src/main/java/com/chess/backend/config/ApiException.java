package com.chess.backend.config;

import org.springframework.http.HttpStatus;
import com.chess.backend.exception.AppException;

public class ApiException extends AppException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(status, status.name(), message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
