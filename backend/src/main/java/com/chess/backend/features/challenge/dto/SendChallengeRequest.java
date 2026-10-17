package com.chess.backend.features.challenge.dto;

import com.chess.backend.features.challenge.domain.enums.GameSide;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record SendChallengeRequest(@NotBlank String toUsername, GameSide challengerSide,
                                   @Min(5) Integer initialTimeSeconds,
                                   @Min(0) Integer incrementSeconds,
                                   String initialFen, List<String> setupMoves, List<String> setupFens) {
    public SendChallengeRequest(String toUsername, GameSide challengerSide, Integer initialTimeSeconds,
                                Integer incrementSeconds) {
        this(toUsername, challengerSide, initialTimeSeconds, incrementSeconds, null, List.of(), List.of());
    }
}
