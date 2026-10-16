package com.chess.backend.features.auth.dto;

public record UserSummary(long id, String username, String displayName, String email) {
}
