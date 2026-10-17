package com.chess.backend.features.challenge.dto;

import com.chess.backend.features.challenge.domain.enums.GameSide;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SendChallengeRequest(@NotBlank String toUsername, GameSide challengerSide,
                                   @Min(5) Integer initialTimeSeconds,
                                   @Min(0) Integer incrementSeconds,
                                   @Size(max = 128) String initialFen,
                                   @Size(max = 300) List<@NotBlank @Size(max = 10) String> setupMoves,
                                   @Size(max = 301) List<@NotBlank @Size(max = 128) String> setupFens) {
    public SendChallengeRequest(String toUsername, GameSide challengerSide, Integer initialTimeSeconds,
                                Integer incrementSeconds) {
        this(toUsername, challengerSide, initialTimeSeconds, incrementSeconds, null, List.of(), List.of());
    }
}
