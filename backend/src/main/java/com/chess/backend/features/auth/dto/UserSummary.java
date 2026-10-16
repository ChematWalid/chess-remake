package com.chess.backend.features.auth.dto;

import java.util.UUID;

public record UserSummary(UUID publicId, String username, String displayName, String email) {
}
