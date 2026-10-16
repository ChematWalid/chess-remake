package com.chess.backend.features.social.dto;

import com.chess.backend.features.social.domain.enums.NotificationType;

public record UserNotification(NotificationType type, String subjectId) {
}
