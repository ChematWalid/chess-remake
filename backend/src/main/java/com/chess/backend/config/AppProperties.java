package com.chess.backend.config;

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
        Duration roomJoinWindow,
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
        DataSize avatarMaxSize,
        String logLevel) {

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
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                || refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()
                || roomTokenTtl == null || roomTokenTtl.isNegative() || roomTokenTtl.isZero()) {
            throw new IllegalStateException("Token lifetimes must be positive");
        }
    }
}
