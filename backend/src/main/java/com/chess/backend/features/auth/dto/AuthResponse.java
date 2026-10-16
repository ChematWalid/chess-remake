package com.chess.backend.features.auth.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds,
                           boolean onboardingRequired, UserSummary user) {
}
