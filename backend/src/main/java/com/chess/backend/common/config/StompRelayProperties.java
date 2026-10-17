package com.chess.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.stomp-relay")
public record StompRelayProperties(String host, int port, String login, String passcode) {
    public StompRelayProperties {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("app.stomp-relay.host is required");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("app.stomp-relay.port must be a valid TCP port");
        }
        if (login == null || login.isBlank() || passcode == null || passcode.isBlank()) {
            throw new IllegalStateException("STOMP relay credentials are required");
        }
    }
}
