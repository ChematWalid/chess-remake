package com.chess.backend.features.challenge.dto;

import com.chess.backend.features.challenge.domain.enums.ChallengeStatus;
import com.chess.backend.features.challenge.domain.enums.GameSide;
import java.time.Instant;
import java.util.UUID;

public record ChallengeDetail(long challengeId, ChallengeStatus status, UUID opponentPublicUserId,
                              String opponentUsername, Integer initialTimeSeconds, Integer incrementSeconds,
                              GameSide challengerSide, GameSide playerColor, String roomCode,
                              String connectUrl, String token, Instant expiresAt) {
}
