package com.chess.backend.features.user.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class AvatarUploadTooLargeException extends AppException {
    public AvatarUploadTooLargeException(String message) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "AVATAR_TOO_LARGE", message);
    }
}
