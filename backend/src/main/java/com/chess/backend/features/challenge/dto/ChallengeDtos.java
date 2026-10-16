package com.chess.backend.features.challenge.dto;

import com.chess.backend.features.challenge.domain.enums.ChallengeStatus;
import com.chess.backend.features.challenge.domain.enums.GameSide;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public final class ChallengeDtos {

    private ChallengeDtos() { }

    public record SendRequest(@NotBlank String toUsername,
                              GameSide challengerSide,
                              @Min(5) Integer initialTimeSeconds,
                              @Min(0) Integer incrementSeconds) { }

    public record ChallengeEvent(long challengeId, ChallengeStatus status, long fromUserId, String fromUsername,
                                 String fromDisplayName, Integer initialTimeSeconds, Integer incrementSeconds,
                                 String challengerSide, String roomCode, String color, String connectUrl,
                                 String token, String message) { }

}
