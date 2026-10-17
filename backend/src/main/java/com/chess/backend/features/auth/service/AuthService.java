package com.chess.backend.features.auth.service;

import com.chess.backend.features.user.exception.UsernameAlreadyTakenException;


import com.chess.backend.common.exception.UserNotFoundException;
import com.chess.backend.features.auth.exception.EmailActionTokenInvalidException;
import com.chess.backend.features.auth.exception.EmailVerificationRequiredException;
import com.chess.backend.features.auth.exception.InvalidCredentialsException;
import com.chess.backend.features.auth.exception.OAuthExchangeCodeInvalidException;
import com.chess.backend.features.auth.exception.OAuthExchangeCodeRequiredException;
import com.chess.backend.features.auth.exception.OAuthOnboardingAlreadyCompleteException;
import com.chess.backend.features.auth.exception.RefreshTokenInvalidException;


import com.chess.backend.features.auth.dto.AuthResponse;
import com.chess.backend.features.auth.dto.RegisterRequest;
import com.chess.backend.features.auth.dto.AuthBundle;
import com.chess.backend.features.auth.dto.UserSummary;
import com.chess.backend.common.config.AppProperties;
import com.chess.backend.features.auth.domain.EmailActionToken;
import com.chess.backend.features.auth.domain.OAuthExchangeCode;
import com.chess.backend.features.auth.domain.RefreshToken;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.auth.domain.enums.EmailAction;
import com.chess.backend.features.auth.repository.EmailActionTokenRepository;
import com.chess.backend.features.auth.repository.OAuthExchangeCodeRepository;
import com.chess.backend.features.auth.repository.RefreshTokenRepository;
import com.chess.backend.features.user.service.UserDirectory;
import com.chess.backend.features.auth.security.JwtService;
import com.chess.backend.common.util.TokenUtils;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int MAX_SESSION_LABEL_LENGTH = 100;

    private final UserDirectory users;
    private final RefreshTokenRepository refreshTokens;
    private final EmailActionTokenRepository actionTokens;
    private final OAuthExchangeCodeRepository oauthCodes;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailSender emailSender;
    private final AppProperties properties;
    private final Clock clock;

    public AuthService(UserDirectory users, RefreshTokenRepository refreshTokens, EmailActionTokenRepository actionTokens,
                       OAuthExchangeCodeRepository oauthCodes,
                       PasswordEncoder passwordEncoder, JwtService jwtService, EmailSender emailSender,
                       AppProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.actionTokens = actionTokens;
        this.oauthCodes = oauthCodes;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? username : request.displayName().trim();
        User user = users.create(new User(username, email, passwordEncoder.encode(request.password()), displayName));
        createAction(user, EmailAction.VERIFY_EMAIL, Duration.ofHours(24), "Verify your Chess account", "/verify-email?token=");
    }

    @Transactional
    public AuthBundle login(String identifier, String password, String sessionLabel) {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        User user = users.findByIdentifier(normalized)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }
        if (!user.isEmailVerified()) {
            throw new EmailVerificationRequiredException("Verify your email before signing in");
        }
        return issueSession(user, sessionLabel);
    }

    @Transactional
    public AuthBundle refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new RefreshTokenInvalidException("Refresh token is missing");
        }
        RefreshToken old = refreshTokens.lockByHash(TokenUtils.sha256(rawToken))
                .filter(token -> token.getExpiresAt().isAfter(clock.instant()) && token.getUser().isEmailVerified())
                .orElseThrow(() -> new RefreshTokenInvalidException("Refresh token is invalid or expired"));
        User user = old.getUser();
        refreshTokens.delete(old);
        refreshTokens.flush();
        return issueSession(user, "browser");
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokens.deleteByTokenHash(TokenUtils.sha256(rawToken));
        }
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailActionToken token = consumeAction(rawToken, EmailAction.VERIFY_EMAIL);
        token.getUser().setEmailVerified(true);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmail(email.trim()).ifPresent(user ->
                createAction(user, EmailAction.RESET_PASSWORD, Duration.ofMinutes(30), "Reset your Chess password", "/reset-password?token="));
    }

    @Transactional
    public void resetPassword(String rawToken, String password) {
        EmailActionToken token = consumeAction(rawToken, EmailAction.RESET_PASSWORD);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(password));
        refreshTokens.deleteByUser_Id(user.getId());
    }

    @Transactional
    public String createOAuthExchangeCode(User user) {
        String raw = TokenUtils.randomToken(32);
        oauthCodes.save(new OAuthExchangeCode(TokenUtils.sha256(raw), user, clock.instant().plus(Duration.ofMinutes(2))));
        return raw;
    }

    @Transactional
    public AuthBundle exchangeOAuthCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) throw new OAuthExchangeCodeRequiredException("OAuth code is required");
        OAuthExchangeCode code = oauthCodes.lockActive(TokenUtils.sha256(rawCode))
                .filter(value -> value.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new OAuthExchangeCodeInvalidException("OAuth code is invalid or expired"));
        code.consume(clock.instant());
        return issueSession(code.getUser(), "oauth-browser");
    }

    @Transactional
    public AuthBundle completeOAuthOnboarding(long userId, String requestedUsername) {
        User user = users.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        if (user.isOnboardingComplete()) throw new OAuthOnboardingAlreadyCompleteException("OAuth onboarding is already complete");
        String username = requestedUsername.trim().toLowerCase(Locale.ROOT);
        users.findByUsername(username).filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> { throw new UsernameAlreadyTakenException("Username is already taken"); });
        user.completeOAuthOnboarding(username);
        refreshTokens.deleteByUser_Id(userId);
        return issueSession(user, "oauth-browser");
    }

    private AuthBundle issueSession(User user, String sessionLabel) {
        String rawToken = TokenUtils.randomToken(48);
        String boundedSessionLabel = sessionLabel == null ? "browser"
                : sessionLabel.substring(0, Math.min(sessionLabel.length(), MAX_SESSION_LABEL_LENGTH));
        refreshTokens.save(new RefreshToken(user, TokenUtils.sha256(rawToken), boundedSessionLabel,
                clock.instant().plus(properties.refreshTokenTtl())));
        String accessToken = jwtService.createAccessToken(user.getId(), user.getUsername(), user.getRole());
        AuthResponse response = new AuthResponse(accessToken, "Bearer", properties.accessTokenTtl().toSeconds(), !user.isOnboardingComplete(),
                new UserSummary(user.getPublicId(), user.getUsername(), user.getDisplayName(), user.getEmail()));
        return new AuthBundle(response, rawToken);
    }

    private void createAction(User user, EmailAction action, Duration ttl, String subject, String frontendPath) {
        actionTokens.deleteByUser_IdAndActionAndConsumedAtIsNull(user.getId(), action);
        String rawToken = TokenUtils.randomToken(32);
        actionTokens.save(new EmailActionToken(user, TokenUtils.sha256(rawToken), action, clock.instant().plus(ttl)));
        emailSender.sendAction(user.getEmail(), subject, "Open this link to continue: "
                + properties.frontendUrl() + frontendPath + rawToken);
    }

    private EmailActionToken consumeAction(String rawToken, EmailAction action) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new EmailActionTokenInvalidException("Action token is required");
        }
        EmailActionToken token = actionTokens.findByTokenHashAndActionAndConsumedAtIsNull(TokenUtils.sha256(rawToken), action)
                .filter(value -> value.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new EmailActionTokenInvalidException("Action token is invalid or expired"));
        token.consume(clock.instant());
        return token;
    }

}
