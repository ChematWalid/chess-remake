package com.chess.backend.features.social.dto;

import java.time.Instant;
import java.util.UUID;
import com.chess.backend.features.social.domain.enums.FriendshipStatus;

public record FriendRequestSummary(long requestId, UUID publicUserId, long internalUserId, String username,
                                   String displayName, FriendshipStatus status, Instant createdAt) {
}
