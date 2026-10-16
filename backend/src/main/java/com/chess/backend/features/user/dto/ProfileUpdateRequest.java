package com.chess.backend.features.user.dto;

import com.chess.backend.features.user.validation.ValidUsername;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @ValidUsername String username,
        @Size(min = 1, max = 80) String displayName,
        @Size(max = 500) String bio,
        Boolean searchable) {
}
