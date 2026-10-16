package com.chess.backend.features.user.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class UnsupportedAvatarMediaTypeException extends AppException {
    public UnsupportedAvatarMediaTypeException(String message) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_AVATAR_MEDIA_TYPE", message);
    }
}
