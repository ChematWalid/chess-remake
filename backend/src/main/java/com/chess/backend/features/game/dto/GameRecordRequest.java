package com.chess.backend.features.game.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record GameRecordRequest(@NotBlank String roomCode, @NotBlank String roomToken, @NotBlank String whitePlayerId,
                                @NotBlank String blackPlayerId, String winnerPlayerId, @NotNull Boolean isDraw,
                                @NotBlank String terminationReason, @JsonAlias("reason_code") String reasonCode,
                                @PositiveOrZero int totalMoves, @PositiveOrZero int durationSeconds, @NotNull String pgn,
                                @NotNull List<String> moves, @NotNull List<String> fens) { }
