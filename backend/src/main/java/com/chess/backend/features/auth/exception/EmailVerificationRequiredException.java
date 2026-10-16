package com.chess.backend.features.auth.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class EmailVerificationRequiredException extends AppException {
    public EmailVerificationRequiredException(String message) {
        super(HttpStatus.FORBIDDEN, "EMAIL_VERIFICATION_REQUIRED", message);
    }
}
