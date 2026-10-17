package com.chess.backend.features.game.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GameRecordRequest(@NotBlank String roomCode, @NotBlank String whitePlayerId,
                                @NotBlank String blackPlayerId, String winnerPlayerId, @NotNull Boolean isDraw,
                                @NotBlank String terminationReason, @JsonAlias("reason_code") String reasonCode,
                                @PositiveOrZero int totalMoves, @PositiveOrZero int durationSeconds, @NotBlank String pgn,
                                @NotNull List<@NotBlank String> moves, @NotNull List<@NotBlank String> fens,
                                String gameType, List<@NotBlank String> setupMoves,
                                List<@NotBlank String> setupFens) { }
