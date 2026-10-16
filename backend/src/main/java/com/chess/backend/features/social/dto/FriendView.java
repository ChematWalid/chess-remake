package com.chess.backend.features.social.dto;

import com.chess.backend.features.social.domain.enums.PresenceStatus;
import java.util.UUID;

public record FriendView(UUID publicUserId, String username, String displayName, String avatarUrl, PresenceStatus presence) {
}
