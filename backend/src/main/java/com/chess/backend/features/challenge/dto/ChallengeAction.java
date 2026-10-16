package com.chess.backend.features.challenge.dto;

import jakarta.validation.constraints.Positive;

public record ChallengeAction(@Positive long challengeId) {
}
