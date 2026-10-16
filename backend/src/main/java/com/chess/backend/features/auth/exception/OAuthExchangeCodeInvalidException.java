package com.chess.backend.features.auth.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class OAuthExchangeCodeInvalidException extends AppException {
    public OAuthExchangeCodeInvalidException(String message) {
        super(HttpStatus.UNAUTHORIZED, "OAUTH_CODE_INVALID", message);
    }
}
