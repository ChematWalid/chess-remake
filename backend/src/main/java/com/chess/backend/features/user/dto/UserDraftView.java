package com.chess.backend.features.user.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record UserDraftView(String featureKey, JsonNode payload, Instant updatedAt) { }
