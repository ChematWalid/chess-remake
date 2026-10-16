package com.chess.backend.features.user.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class AvatarUploadEmptyException extends AppException {
    public AvatarUploadEmptyException(String message) {
        super(HttpStatus.BAD_REQUEST, "AVATAR_UPLOAD_EMPTY", message);
    }
}
