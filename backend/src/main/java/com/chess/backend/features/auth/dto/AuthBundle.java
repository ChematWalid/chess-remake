package com.chess.backend.features.auth.dto;

public record AuthBundle(AuthResponse response, String refreshToken) {
}
