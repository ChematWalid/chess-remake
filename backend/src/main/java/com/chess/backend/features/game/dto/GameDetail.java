package com.chess.backend.features.game.dto;

import java.util.List;

public record GameDetail(GameSummary summary, String pgn, List<String> moves, List<String> fens,
                         Integer initialTimeSeconds, Integer incrementSeconds, int durationSeconds,
                         String gameType, List<String> setupMoves, List<String> setupFens) {
}
