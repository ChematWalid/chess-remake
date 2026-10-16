package com.chess.backend.features.social.dto;

import com.chess.backend.features.social.domain.enums.PresenceStatus;

public record PresenceEvent(long userId, PresenceStatus status) {
}
