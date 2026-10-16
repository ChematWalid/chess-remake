package com.chess.backend.features.challenge.dto;

import com.chess.backend.features.challenge.domain.enums.ChallengeStatus;
import com.chess.backend.features.challenge.domain.enums.ChallengeEventType;
import com.chess.backend.features.challenge.domain.enums.GameSide;
import java.util.UUID;

public record ChallengeEvent(long challengeId, ChallengeEventType eventType, ChallengeStatus status,
                             UUID actorPublicUserId, String otherUsername,
                             String otherDisplayName, Integer initialTimeSeconds, Integer incrementSeconds,
                             GameSide challengerSide, String roomCode, GameSide color, String connectUrl,
                             String token, String message) {
}
