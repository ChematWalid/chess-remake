package com.chess.backend.features.user.dto;

import com.chess.backend.common.security.OwnerScopedResponse;
import com.chess.backend.common.security.Sensitive;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record Profile(@Sensitive long id, String username, String displayName, String bio, String avatarUrl,
                      boolean searchable, int totalGames, int wins, int losses, int draws,
                      @JsonIgnore long ownerId) implements OwnerScopedResponse {
}
