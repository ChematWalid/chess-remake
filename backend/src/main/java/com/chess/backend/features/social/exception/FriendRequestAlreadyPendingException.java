package com.chess.backend.features.social.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class FriendRequestAlreadyPendingException extends AppException {
    public FriendRequestAlreadyPendingException(String message) {
        super(HttpStatus.CONFLICT, "FRIEND_REQUEST_ALREADY_PENDING", message);
    }
}
