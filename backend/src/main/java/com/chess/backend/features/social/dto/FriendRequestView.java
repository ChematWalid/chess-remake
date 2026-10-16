package com.chess.backend.features.social.dto;

import java.time.Instant;

public record FriendRequestView(long requestId, long userId, String username, String displayName,
                               String avatarUrl, Instant createdAt) {
}
