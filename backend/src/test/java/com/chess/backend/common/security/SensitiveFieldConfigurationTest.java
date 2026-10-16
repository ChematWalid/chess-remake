package com.chess.backend.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chess.backend.common.domain.enums.UserRole;
import com.chess.backend.features.user.dto.Profile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

class SensitiveFieldConfigurationTest {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new SensitiveFieldConfiguration().sensitiveFieldModule());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void serializesSensitiveFieldsForTheirOwner() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticationFor(42));
        Profile profile = profileOwnedBy(42);

        String json = objectMapper.writeValueAsString(profile);

        assertTrue(json.contains("\"id\":42"));
        assertFalse(json.contains("ownerId"));
    }

    @Test
    void omitsSensitiveFieldsForOtherUsersAndAnonymousViewers() throws Exception {
        Profile profile = profileOwnedBy(42);

        String anonymousJson = objectMapper.writeValueAsString(profile);
        SecurityContextHolder.getContext().setAuthentication(authenticationFor(9));
        String otherUserJson = objectMapper.writeValueAsString(profile);

        assertFalse(anonymousJson.contains("\"id\":42"));
        assertFalse(otherUserJson.contains("\"id\":42"));
        assertTrue(anonymousJson.contains("\"publicId\":"));
        assertTrue(otherUserJson.contains("\"publicId\":"));
    }

    private UsernamePasswordAuthenticationToken authenticationFor(long userId) {
        ChessPrincipal principal = new ChessPrincipal(userId, "player", UserRole.ROLE_USER);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private Profile profileOwnedBy(long userId) {
        return new Profile(userId, UUID.randomUUID(), "player", "Player", null, null, true, 0, 0, 0, 0, userId);
    }
}
