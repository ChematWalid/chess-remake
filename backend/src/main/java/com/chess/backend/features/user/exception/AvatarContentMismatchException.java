package com.chess.backend.features.user.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class AvatarContentMismatchException extends AppException {
    public AvatarContentMismatchException(String message) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "AVATAR_CONTENT_MISMATCH", message);
    }
}
