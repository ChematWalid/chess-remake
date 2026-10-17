package com.chess.backend.features.challenge.dto;

import com.chess.backend.features.challenge.domain.enums.ChallengeStatus;
import com.chess.backend.features.challenge.domain.enums.GameSide;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record ChallengeSummary(long challengeId, ChallengeStatus status, UUID challengerPublicUserId,
                               String challengerUsername, UUID challengedPublicUserId, String challengedUsername,
                               Integer initialTimeSeconds, Integer incrementSeconds, GameSide challengerSide,
                               GameSide playerColor, String roomCode, Instant createdAt, Instant expiresAt,
                               String initialFen, List<String> setupMoves, List<String> setupFens) {
}
