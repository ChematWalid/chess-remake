package com.chess.backend.features.social.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class FriendInviteInvalidException extends AppException {
    public FriendInviteInvalidException(String message) {
        super(HttpStatus.NOT_FOUND, "FRIEND_INVITE_INVALID", message);
    }
}
