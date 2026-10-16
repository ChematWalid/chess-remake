package com.chess.backend.common.api;

public record UserDestinationMessage(long recipientUserId, String destination, Object payload) {
}
