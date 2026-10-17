package com.chess.backend.features.user.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class UserDraftLimitExceededException extends AppException {
    public UserDraftLimitExceededException() {
        super(HttpStatus.CONFLICT, "USER_DRAFT_LIMIT_EXCEEDED", "An account can store up to 20 drafts.");
    }
}
