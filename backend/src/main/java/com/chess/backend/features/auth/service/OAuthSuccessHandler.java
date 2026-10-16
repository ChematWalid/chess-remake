package com.chess.backend.features.auth.service;

import com.chess.backend.common.config.AppProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuthSuccessHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private final OAuthAccountService accounts;
    private final AppProperties properties;
    public OAuthSuccessHandler(OAuthAccountService accounts, AppProperties properties) { this.accounts = accounts; this.properties = properties; }
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        try {
            String code = accounts.authenticate(oauth);
            String target = UriComponentsBuilder.fromUriString(properties.frontendUrl())
                    .path("/oauth2/redirect").queryParam("code", code).build().toUriString();
            response.sendRedirect(target);
        } catch (RuntimeException exception) {
            redirectFailure(response);
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        redirectFailure(response);
    }

    private void redirectFailure(HttpServletResponse response) throws IOException {
        String target = UriComponentsBuilder.fromUriString(properties.frontendUrl()).path("/oauth2/redirect")
                .queryParam("errorCode", "OAUTH_AUTHENTICATION_FAILED")
                .queryParam("message", "OAuth sign-in could not be completed").build().toUriString();
        response.sendRedirect(target);
    }
}
