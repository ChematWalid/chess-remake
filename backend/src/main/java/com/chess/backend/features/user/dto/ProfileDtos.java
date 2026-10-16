package com.chess.backend.features.user.dto;

import jakarta.validation.constraints.Size;
import com.chess.backend.common.security.OwnerScopedResponse;
import com.chess.backend.common.security.Sensitive;
import com.chess.backend.features.user.validation.ValidUsername;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record UpdateRequest(
            @ValidUsername String username,
            @Size(min = 1, max = 80) String displayName,
            @Size(max = 500) String bio,
            Boolean searchable) {
    }

    public record Profile(@Sensitive long id, String username, String displayName, String bio, String avatarUrl,
                          boolean searchable, int totalGames, int wins, int losses, int draws,
                          @JsonIgnore long ownerId) implements OwnerScopedResponse {
    }

    public record SearchResult(@Sensitive long id, String username, String displayName, String avatarUrl,
                               int totalGames, int wins, int losses, int draws,
                               @JsonIgnore long ownerId) implements OwnerScopedResponse {
    }

    public record Page<T>(List<T> items, int page, int size, long totalElements) {
    }
}
