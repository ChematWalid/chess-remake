package com.chess.backend.features.auth.service;

import com.chess.backend.features.auth.domain.OAuthIdentity;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.auth.repository.OAuthIdentityRepository;
import com.chess.backend.features.user.service.UserDirectory;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class OAuthAccountService {
    private final UserDirectory users;
    private final OAuthIdentityRepository identities;
    private final OAuth2AuthorizedClientService authorizedClients;
    private final AuthService authService;
    private final RestClient restClient = RestClient.create();

    public OAuthAccountService(UserDirectory users, OAuthIdentityRepository identities,
                               OAuth2AuthorizedClientService authorizedClients, AuthService authService) {
        this.users = users; this.identities = identities; this.authorizedClients = authorizedClients; this.authService = authService;
    }

    @Transactional
    public String authenticate(OAuth2AuthenticationToken authentication) {
        String provider = authentication.getAuthorizedClientRegistrationId().toLowerCase(Locale.ROOT);
        OAuth2User principal = authentication.getPrincipal();
        String subject = subject(provider, principal);
        VerifiedIdentity verified = provider.equals("github") ? githubIdentity(authentication, principal) : googleIdentity(principal);
        User user = identities.findByProviderAndProviderSubject(provider, subject).map(OAuthIdentity::getUser).orElseGet(() -> {
            User byEmail = users.findByEmail(verified.email()).orElse(null);
            if (byEmail != null) {
                if (!byEmail.isEmailVerified()) throw oauthFailure("Email must be verified before linking this account");
                identities.save(new OAuthIdentity(byEmail, provider, subject));
                return byEmail;
            }
            User created = new User(temporaryUsername(), verified.email(), null,
                    verified.displayName() == null || verified.displayName().isBlank() ? "Chess Player" : verified.displayName());
            created.setEmailVerified(true);
            created.requireOAuthOnboarding();
            User saved = users.create(created);
            identities.save(new OAuthIdentity(saved, provider, subject));
            return saved;
        });
        return authService.createOAuthExchangeCode(user);
    }

    private VerifiedIdentity googleIdentity(OAuth2User principal) {
        String email = principal.getAttribute("email");
        Boolean verified = principal.getAttribute("email_verified");
        if (email == null || !Boolean.TRUE.equals(verified)) throw oauthFailure("A verified provider email is required");
        return new VerifiedIdentity(email, principal.getAttribute("name"));
    }

    private VerifiedIdentity githubIdentity(OAuth2AuthenticationToken authentication, OAuth2User principal) {
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(authentication.getAuthorizedClientRegistrationId(), authentication.getName());
        if (client == null) throw oauthFailure("OAuth authorization could not be verified");
        List<Map<String, Object>> emails = restClient.get().uri("https://api.github.com/user/emails")
                .headers(headers -> headers.setBearerAuth(client.getAccessToken().getTokenValue()))
                .header("Accept", "application/vnd.github+json")
                .retrieve().body(new ParameterizedTypeReference<>() { });
        if (emails == null) throw oauthFailure("A verified provider email is required");
        String email = emails.stream().filter(item -> Boolean.TRUE.equals(item.get("primary")) && Boolean.TRUE.equals(item.get("verified")))
                .map(item -> item.get("email")).filter(String.class::isInstance).map(String.class::cast).findFirst()
                .orElseThrow(() -> oauthFailure("A verified provider email is required"));
        String displayName = principal.getAttribute("name");
        return new VerifiedIdentity(email, displayName == null ? principal.getAttribute("login") : displayName);
    }

    private static String subject(String provider, OAuth2User principal) {
        Object value = principal.getAttribute(provider.equals("google") ? "sub" : "id");
        if (value == null) throw oauthFailure("OAuth provider identity is incomplete");
        return value.toString();
    }

    private String temporaryUsername() {
        String candidate;
        do { candidate = "oauth_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
        while (users.findByUsername(candidate).isPresent());
        return candidate;
    }

    private static OAuth2AuthenticationException oauthFailure(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"), message);
    }
    private record VerifiedIdentity(String email, String displayName) { }
}
