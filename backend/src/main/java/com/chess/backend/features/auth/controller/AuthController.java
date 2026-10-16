package com.chess.backend.features.auth.controller;

import com.chess.backend.features.auth.dto.AuthBundle;


import com.chess.backend.features.auth.service.AuthService;
import com.chess.backend.features.auth.dto.EmailRequest;
import com.chess.backend.features.auth.dto.LoginRequest;
import com.chess.backend.features.auth.dto.OAuthExchangeRequest;
import com.chess.backend.features.auth.dto.OAuthOnboardingRequest;
import com.chess.backend.features.auth.dto.RegisterRequest;
import com.chess.backend.features.auth.dto.ResetPasswordRequest;
import com.chess.backend.features.auth.dto.TokenRequest;
import com.chess.backend.common.config.AppProperties;
import com.chess.backend.common.security.ChessPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "chess_refresh";
    private final AuthService authService;
    private final AppProperties properties;

    public AuthController(AuthService authService, AppProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/register")
    ResponseEntity<?> register(@NotNull @Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.accepted().body(Map.of("message", "Check your email to verify your account"));
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@NotNull @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthBundle result = authService.login(request.identifier(), request.password(),
                servletRequest.getHeader("User-Agent"));
        return withRefreshCookie(result);
    }

    @PostMapping("/refresh")
    ResponseEntity<?> refresh(HttpServletRequest request) {
        AuthBundle result = authService.refresh(readCookie(request, REFRESH_COOKIE));
        return withRefreshCookie(result);
    }

    @PostMapping("/logout")
    ResponseEntity<?> logout(HttpServletRequest request) {
        authService.logout(readCookie(request, REFRESH_COOKIE));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    @PostMapping("/verify-email")
    ResponseEntity<?> verify(@NotNull @Valid @RequestBody TokenRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.ok(Map.of("message", "Email verified"));
    }

    @PostMapping("/forgot-password")
    ResponseEntity<?> forgot(@NotNull @Valid @RequestBody EmailRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().body(Map.of("message", "If the account exists, reset instructions were sent"));
    }

    @PostMapping("/reset-password")
    ResponseEntity<?> reset(@NotNull @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/oauth/exchange")
    ResponseEntity<?> exchangeOAuth(@NotNull @Valid @RequestBody OAuthExchangeRequest request) {
        return withRefreshCookie(authService.exchangeOAuthCode(request.code()));
    }

    @PostMapping("/oauth/onboarding")
    ResponseEntity<?> completeOAuthOnboarding(@AuthenticationPrincipal ChessPrincipal principal,
                                               @NotNull @Valid @RequestBody OAuthOnboardingRequest request) {
        return withRefreshCookie(authService.completeOAuthOnboarding(principal.userId(), request.username()));
    }

    private ResponseEntity<?> withRefreshCookie(AuthBundle result) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, result.refreshToken())
                .httpOnly(true).secure(properties.cookieSecure()).sameSite(properties.cookieSameSite())
                .path("/api/auth").maxAge(properties.refreshTokenTtl()).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(result.response());
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite()).path("/api/auth").maxAge(0).build();
    }

    private static String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
