package com.chess.backend.features.social.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class SelfFriendActionException extends AppException {
    public SelfFriendActionException(String message) {
        super(HttpStatus.BAD_REQUEST, "SELF_FRIEND_ACTION", message);
    }
}
