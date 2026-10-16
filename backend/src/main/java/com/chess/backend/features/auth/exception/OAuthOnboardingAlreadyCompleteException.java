package com.chess.backend.features.auth.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class OAuthOnboardingAlreadyCompleteException extends AppException {
    public OAuthOnboardingAlreadyCompleteException(String message) {
        super(HttpStatus.CONFLICT, "OAUTH_ONBOARDING_ALREADY_COMPLETE", message);
    }
}
