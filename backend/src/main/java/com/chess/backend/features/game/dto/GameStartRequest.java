package com.chess.backend.features.game.dto;

import jakarta.validation.constraints.NotBlank;

public record GameStartRequest(
        @NotBlank String roomCode,
        @NotBlank String whitePlayerId,
        @NotBlank String blackPlayerId) {
}
