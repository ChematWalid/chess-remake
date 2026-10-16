package com.chess.backend.challenge;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public final class ChallengeDtos {

    private ChallengeDtos() { }

    public record SendRequest(@NotBlank String toUsername,
                              SidePreference challengerSide,
                              @Min(5) Integer initialTimeSeconds,
                              @Min(0) Integer incrementSeconds) { }

    public record ChallengeEvent(long challengeId, String status, long fromUserId, String fromUsername,
                                 String fromDisplayName, Integer initialTimeSeconds, Integer incrementSeconds,
                                 String challengerSide, String roomCode, String color, String connectUrl,
                                 String token, String message) { }

    public enum SidePreference { WHITE, BLACK, RANDOM }
}
