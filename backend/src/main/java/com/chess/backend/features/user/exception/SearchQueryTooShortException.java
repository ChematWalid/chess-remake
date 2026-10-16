package com.chess.backend.features.user.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class SearchQueryTooShortException extends AppException {
    public SearchQueryTooShortException(String message) {
        super(HttpStatus.BAD_REQUEST, "SEARCH_QUERY_TOO_SHORT", message);
    }
}
