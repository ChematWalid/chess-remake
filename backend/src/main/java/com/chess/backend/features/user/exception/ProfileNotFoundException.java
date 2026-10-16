package com.chess.backend.features.user.exception;

import com.chess.backend.common.exception.NotFoundException;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class ProfileNotFoundException extends AppException {
    public ProfileNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", message);
    }
}
