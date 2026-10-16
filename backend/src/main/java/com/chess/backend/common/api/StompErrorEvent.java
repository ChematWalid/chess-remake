package com.chess.backend.common.api;

public record StompErrorEvent(String errorCode, String message) {
}
