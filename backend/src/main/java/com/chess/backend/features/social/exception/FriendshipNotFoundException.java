package com.chess.backend.features.social.exception;


import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class FriendshipNotFoundException extends AppException {
    public FriendshipNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "FRIENDSHIP_NOT_FOUND", message);
    }
}
