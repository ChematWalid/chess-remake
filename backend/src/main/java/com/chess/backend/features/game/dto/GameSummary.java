package com.chess.backend.features.game.dto;

import java.time.Instant;
import java.util.UUID;

public record GameSummary(long id, String whiteUsername, String blackUsername, boolean draw,
                          UUID whitePublicUserId, UUID blackPublicUserId, UUID winnerPublicUserId,
                          String terminationReason, int totalMoves, Instant playedAt, String gameType) {
}
