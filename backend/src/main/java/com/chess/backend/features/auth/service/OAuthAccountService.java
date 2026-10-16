package com.chess.backend.features.auth.service;

import com.chess.backend.features.auth.domain.OAuthIdentity;
import com.chess.backend.features.auth.dto.OAuthVerifiedIdentity;
import com.chess.backend.features.auth.repository.OAuthIdentityRepository;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.user.service.UserDirectory;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthAccountService {
    private static final String PROVIDER = "google";
    private final UserDirectory users;
    private final OAuthIdentityRepository identities;
    private final AuthService authService;

    public OAuthAccountService(UserDirectory users, OAuthIdentityRepository identities, AuthService authService) {
        this.users = users;
        this.identities = identities;
        this.authService = authService;
    }

    @Transactional
    public String authenticate(OAuth2AuthenticationToken authentication) {
        if (authentication == null || authentication.getPrincipal() == null
                || !PROVIDER.equals(authentication.getAuthorizedClientRegistrationId())) {
            throw oauthFailure("Google identity is missing");
        }
        OAuth2User principal = authentication.getPrincipal();
        String subject = principal.getAttribute("sub");
        if (subject == null || subject.isBlank()) throw oauthFailure("Google identity is incomplete");
        OAuthVerifiedIdentity verified = verifiedIdentity(principal);
        String normalizedEmail = verified.email().trim().toLowerCase(Locale.ROOT);
        User user = identities.findByProviderAndProviderSubject(PROVIDER, subject)
                .map(OAuthIdentity::getUser)
                .orElseGet(() -> findOrCreate(normalizedEmail, verified.displayName(), subject));
        return authService.createOAuthExchangeCode(user);
    }

    private User findOrCreate(String email, String displayName, String subject) {
        User existing = users.findByEmail(email).orElse(null);
        if (existing != null) {
            if (!existing.isEmailVerified()) throw oauthFailure("Email must be verified before linking this account");
            identities.save(new OAuthIdentity(existing, PROVIDER, subject));
            return existing;
        }
        User created = new User(temporaryUsername(), email, null,
                displayName == null || displayName.isBlank() ? "Chess Player" : displayName.trim());
        created.setEmailVerified(true);
        created.requireOAuthOnboarding();
        User saved = users.create(created);
        identities.save(new OAuthIdentity(saved, PROVIDER, subject));
        return saved;
    }

    private static OAuthVerifiedIdentity verifiedIdentity(OAuth2User principal) {
        String email = principal.getAttribute("email");
        Boolean verified = principal.getAttribute("email_verified");
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(verified)) {
            throw oauthFailure("A verified Google email is required");
        }
        return new OAuthVerifiedIdentity(email, principal.getAttribute("name"));
    }

    private String temporaryUsername() {
        String candidate;
        do {
            candidate = "oauth_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (users.findByUsername(candidate).isPresent());
        return candidate;
    }

    private static OAuth2AuthenticationException oauthFailure(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"), message);
    }
}
