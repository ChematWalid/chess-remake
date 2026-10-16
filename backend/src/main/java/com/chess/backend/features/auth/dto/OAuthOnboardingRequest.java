package com.chess.backend.features.auth.dto;

import com.chess.backend.features.user.validation.ValidUsername;
import jakarta.validation.constraints.NotBlank;

public record OAuthOnboardingRequest(@NotBlank @ValidUsername String username) {
}
