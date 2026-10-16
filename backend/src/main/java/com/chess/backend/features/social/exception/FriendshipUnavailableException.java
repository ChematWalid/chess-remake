package com.chess.backend.features.social.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class FriendshipUnavailableException extends AppException {
    public FriendshipUnavailableException(String message) {
        super(HttpStatus.FORBIDDEN, "FRIENDSHIP_UNAVAILABLE", message);
    }
}
