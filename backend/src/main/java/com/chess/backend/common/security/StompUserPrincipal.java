package com.chess.backend.common.security;

import java.security.Principal;

public record StompUserPrincipal(long userId) implements Principal {
    @Override
    public String getName() {
        return Long.toString(userId);
    }
}
