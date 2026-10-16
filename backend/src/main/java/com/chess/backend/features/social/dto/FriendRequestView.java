package com.chess.backend.features.social.dto;

import java.time.Instant;
import java.util.UUID;
import com.chess.backend.features.social.domain.enums.FriendshipStatus;

public record FriendRequestView(long requestId, UUID publicUserId, String username, String displayName,
                               FriendshipStatus status, String avatarUrl, Instant createdAt) {
}
