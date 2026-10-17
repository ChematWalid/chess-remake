package com.chess.backend.features.user.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record UserDraftRequest(@NotNull JsonNode payload) { }
