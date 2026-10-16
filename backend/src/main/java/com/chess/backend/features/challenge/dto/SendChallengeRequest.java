package com.chess.backend.features.challenge.dto;

import com.chess.backend.features.challenge.domain.enums.GameSide;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SendChallengeRequest(@NotBlank String toUsername, GameSide challengerSide,
                                   @Min(5) Integer initialTimeSeconds,
                                   @Min(0) Integer incrementSeconds) {
}
