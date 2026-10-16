package com.chess.backend.features.auth.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class OAuthExchangeCodeRequiredException extends AppException {
    public OAuthExchangeCodeRequiredException(String message) {
        super(HttpStatus.BAD_REQUEST, "OAUTH_CODE_REQUIRED", message);
    }
}
