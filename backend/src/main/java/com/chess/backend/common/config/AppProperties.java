package com.chess.backend.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String frontendUrl,
        String engineWsUrl,
        String corsOrigins,
        String jwtSecret,
        String engineJwtSecret,
        String internalToken,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration roomTokenTtl,
        Duration invitationTtl,
        boolean cookieSecure,
        String cookieSameSite,
        String emailFrom,
        String storageProvider,
        String s3Endpoint,
        String s3Region,
        String s3Bucket,
        String s3AccessKey,
        String s3SecretKey,
        boolean s3PathStyle,
        DataSize avatarMaxSize) {

    public AppProperties {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            throw new IllegalStateException("app.frontend-url is required");
        }
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt-secret must contain at least 32 UTF-8 bytes");
        }
        if (engineJwtSecret == null || engineJwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.engine-jwt-secret must contain at least 32 UTF-8 bytes");
        }
        if (internalToken == null || internalToken.length() < 32) {
            throw new IllegalStateException("app.internal-token must contain at least 32 characters");
        }
        requireText(engineWsUrl, "app.engine-ws-url");
        requireText(corsOrigins, "app.cors-origins");
        requireText(cookieSameSite, "app.cookie-same-site");
        requireText(emailFrom, "app.email-from");
        requireText(storageProvider, "app.storage-provider");
        requireText(s3Endpoint, "app.s3-endpoint");
        requireText(s3Region, "app.s3-region");
        requireText(s3Bucket, "app.s3-bucket");
        requireText(s3AccessKey, "app.s3-access-key");
        requireText(s3SecretKey, "app.s3-secret-key");
        if (!cookieSameSite.equalsIgnoreCase("Strict") && !cookieSameSite.equalsIgnoreCase("Lax")
                && !cookieSameSite.equalsIgnoreCase("None")) {
            throw new IllegalStateException("app.cookie-same-site must be Strict, Lax, or None");
        }
        if (cookieSameSite.equalsIgnoreCase("None") && !cookieSecure) {
            throw new IllegalStateException("Secure cookies are required when app.cookie-same-site is None");
        }
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                || refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()
                || roomTokenTtl == null || roomTokenTtl.isNegative() || roomTokenTtl.isZero()
                || invitationTtl == null || invitationTtl.isNegative() || invitationTtl.isZero()) {
            throw new IllegalStateException("Token lifetimes must be positive");
        }
        if (avatarMaxSize == null || avatarMaxSize.toBytes() <= 0) {
            throw new IllegalStateException("app.avatar-max-size must be positive");
        }
    }

    private static void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " is required");
        }
    }
}
