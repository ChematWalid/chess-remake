package com.chess.backend.features.social.dto;

import jakarta.validation.constraints.NotBlank;

public record RedeemInviteRequest(@NotBlank String token) {
}
