package com.chess.backend.auth;

import com.chess.backend.auth.AuthDtos.AuthResponse;
import com.chess.backend.auth.AuthDtos.RegisterRequest;
import com.chess.backend.auth.AuthDtos.UserSummary;
import com.chess.backend.config.ApiException;
import com.chess.backend.config.AppProperties;
import com.chess.backend.domain.entity.EmailActionToken;
import com.chess.backend.domain.entity.OAuthExchangeCode;
import com.chess.backend.domain.entity.RefreshToken;
import com.chess.backend.domain.entity.User;
import com.chess.backend.repository.EmailActionTokenRepository;
import com.chess.backend.repository.OAuthExchangeCodeRepository;
import com.chess.backend.repository.RefreshTokenRepository;
import com.chess.backend.repository.UserRepository;
import com.chess.backend.security.JwtService;
import com.chess.backend.util.TokenUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final EmailActionTokenRepository actionTokens;
    private final OAuthExchangeCodeRepository oauthCodes;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailSender emailSender;
    private final AppProperties properties;
    private final Clock clock;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, EmailActionTokenRepository actionTokens,
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
        User user = users.save(new User(username, email, passwordEncoder.encode(request.password()), displayName));
        createAction(user, "VERIFY_EMAIL", Duration.ofHours(24), "Verify your Chess account", "/verify-email?token=");
    }

    @Transactional
    public AuthBundle login(String identifier, String password, String sessionLabel) {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        User user = users.findByUsernameIgnoreCaseOrEmailIgnoreCase(normalized, normalized)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Verify your email before signing in");
        }
        return issueSession(user, sessionLabel);
    }

    @Transactional
    public AuthBundle refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }
        RefreshToken old = refreshTokens.lockByHash(TokenUtils.sha256(rawToken))
                .filter(token -> token.getExpiresAt().isAfter(clock.instant()) && token.getUser().isEmailVerified())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired"));
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
        EmailActionToken token = consumeAction(rawToken, "VERIFY_EMAIL");
        token.getUser().setEmailVerified(true);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmailIgnoreCase(email.trim()).ifPresent(user ->
                createAction(user, "RESET_PASSWORD", Duration.ofMinutes(30), "Reset your Chess password", "/reset-password?token="));
    }

    @Transactional
    public void resetPassword(String rawToken, String password) {
        EmailActionToken token = consumeAction(rawToken, "RESET_PASSWORD");
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
        if (rawCode == null || rawCode.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "OAuth code is required");
        OAuthExchangeCode code = oauthCodes.lockActive(TokenUtils.sha256(rawCode))
                .filter(value -> value.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "OAuth code is invalid or expired"));
        code.consume(clock.instant());
        return issueSession(code.getUser(), "oauth-browser");
    }

    @Transactional
    public AuthBundle completeOAuthOnboarding(long userId, String requestedUsername) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isOnboardingComplete()) throw new ApiException(HttpStatus.CONFLICT, "OAuth onboarding is already complete");
        String username = requestedUsername.trim().toLowerCase(Locale.ROOT);
        users.findByUsernameIgnoreCase(username).filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> { throw new ApiException(HttpStatus.CONFLICT, "Username is already taken"); });
        user.completeOAuthOnboarding(username);
        refreshTokens.deleteByUser_Id(userId);
        return issueSession(user, "oauth-browser");
    }

    private AuthBundle issueSession(User user, String sessionLabel) {
        String rawToken = TokenUtils.randomToken(48);
        refreshTokens.save(new RefreshToken(user, TokenUtils.sha256(rawToken), sessionLabel,
                clock.instant().plus(properties.refreshTokenTtl())));
        String accessToken = jwtService.createAccessToken(user.getId(), user.getUsername(), user.getRole());
        AuthResponse response = new AuthResponse(accessToken, "Bearer", properties.accessTokenTtl().toSeconds(), !user.isOnboardingComplete(),
                new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail()));
        return new AuthBundle(response, rawToken);
    }

    private void createAction(User user, String action, Duration ttl, String subject, String frontendPath) {
        actionTokens.deleteByUser_IdAndActionAndConsumedAtIsNull(user.getId(), action);
        String rawToken = TokenUtils.randomToken(32);
        actionTokens.save(new EmailActionToken(user, TokenUtils.sha256(rawToken), action, clock.instant().plus(ttl)));
        emailSender.sendAction(user.getEmail(), subject, "Open this link to continue: "
                + properties.frontendUrl() + frontendPath + rawToken);
    }

    private EmailActionToken consumeAction(String rawToken, String action) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Action token is required");
        }
        EmailActionToken token = actionTokens.findByTokenHashAndActionAndConsumedAtIsNull(TokenUtils.sha256(rawToken), action)
                .filter(value -> value.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Action token is invalid or expired"));
        token.consume(clock.instant());
        return token;
    }

    public record AuthBundle(AuthResponse response, String refreshToken) { }
}
