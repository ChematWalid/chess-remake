package com.chess.backend.features.user.dto;

import com.chess.backend.common.security.OwnerScopedResponse;
import com.chess.backend.common.security.Sensitive;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

public record Profile(@Sensitive long id, UUID publicId, String username, String displayName, String bio, String avatarUrl,
                      boolean searchable, int totalGames, int wins, int losses, int draws,
                      @JsonIgnore long ownerId) implements OwnerScopedResponse {
}
