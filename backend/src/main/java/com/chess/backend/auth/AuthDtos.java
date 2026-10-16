package com.chess.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.chess.backend.validation.ValidUsername;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @ValidUsername String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @Size(max = 80) String displayName) {
    }

    public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {
    }

    public record TokenRequest(@NotBlank String token) {
    }

    public record EmailRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 10, max = 72) String password) {
    }

    public record OAuthExchangeRequest(@NotBlank String code) { }
    public record OAuthOnboardingRequest(@NotBlank @ValidUsername String username) { }

    public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, boolean onboardingRequired, UserSummary user) {
    }

    public record UserSummary(long id, String username, String displayName, String email) {
    }
}
