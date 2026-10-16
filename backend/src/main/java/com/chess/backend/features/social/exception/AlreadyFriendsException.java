package com.chess.backend.features.social.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class AlreadyFriendsException extends AppException {
    public AlreadyFriendsException(String message) {
        super(HttpStatus.CONFLICT, "ALREADY_FRIENDS", message);
    }
}
