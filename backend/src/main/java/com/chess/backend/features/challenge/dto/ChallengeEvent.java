package com.chess.backend.features.challenge.dto;

import com.chess.backend.features.challenge.domain.enums.ChallengeStatus;
import com.chess.backend.features.challenge.domain.enums.GameSide;

public record ChallengeEvent(long challengeId, ChallengeStatus status, long fromUserId, String fromUsername,
                             String fromDisplayName, Integer initialTimeSeconds, Integer incrementSeconds,
                             GameSide challengerSide, String roomCode, GameSide color, String connectUrl,
                             String token, String message) {
}
