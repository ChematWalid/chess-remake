package com.chess.backend.features.user.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class AvatarUploadMissingException extends AppException {
    public AvatarUploadMissingException(String message) {
        super(HttpStatus.BAD_REQUEST, "AVATAR_UPLOAD_MISSING", message);
    }
}
