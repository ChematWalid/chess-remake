package com.chess.backend.features.social.dto;

import com.chess.backend.features.social.domain.enums.PresenceStatus;

public record FriendView(long id, String username, String displayName, String avatarUrl, PresenceStatus presence) {
}
