package com.chess.backend.features.social.exception;

import com.chess.backend.common.exception.NotFoundException;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class FriendRequestNotFoundException extends AppException {
    public FriendRequestNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "FRIEND_REQUEST_NOT_FOUND", message);
    }
}
