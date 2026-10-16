package com.chess.backend.features.game.dto;

import java.time.Instant;

public record GameSummary(long id, String whiteUsername, String blackUsername, boolean draw,
                          Long winnerId, String terminationReason, int totalMoves, Instant playedAt) {
}
