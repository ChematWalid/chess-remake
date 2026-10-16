package com.chess.backend.features.social.dto;

import java.time.Instant;

public record FriendRequestSummary(long requestId, long userId, String username,
                                   String displayName, Instant createdAt) {
}
