package com.chess.backend.features.challenge.exception;

import com.chess.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class RoomCodeAllocationException extends AppException {
    public RoomCodeAllocationException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "ROOM_CODE_ALLOCATION_FAILED", message);
    }
}
