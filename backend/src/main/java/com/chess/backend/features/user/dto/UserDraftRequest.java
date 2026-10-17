package com.chess.backend.features.user.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UserDraftRequest(@NotNull JsonNode payload, Instant editedAt) { }
