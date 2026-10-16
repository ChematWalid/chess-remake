package com.chess.backend.features.social.dto;

import java.util.UUID;

public record FriendSummary(UUID publicUserId, long userId, String username, String displayName) {
}
