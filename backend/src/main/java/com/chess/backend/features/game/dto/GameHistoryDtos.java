package com.chess.backend.features.game.dto;

import java.time.Instant;
import java.util.List;

public final class GameHistoryDtos {
    private GameHistoryDtos() {
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements) {
    }

    public record GameSummary(long id, String whiteUsername, String blackUsername, boolean draw,
                              Long winnerPlayerId, String terminationReason, int totalMoves, Instant playedAt) {
    }

    public record GameDetail(GameSummary summary, String pgn, List<String> moves, List<String> fens,
                             Integer initialTimeSeconds, Integer incrementSeconds, int durationSeconds) {
    }
}
