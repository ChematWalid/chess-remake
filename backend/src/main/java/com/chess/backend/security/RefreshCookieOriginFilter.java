package com.chess.backend.security;

import com.chess.backend.api.AppResponse;
import com.chess.backend.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class RefreshCookieOriginFilter extends OncePerRequestFilter {
    private final Set<String> origins;
    private final ObjectMapper objectMapper;
    public RefreshCookieOriginFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.origins = Arrays.stream(properties.corsOrigins().split(",")).map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        this.objectMapper = objectMapper;
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/auth/refresh") && !path.equals("/api/auth/logout");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || !origins.contains(origin)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), AppResponse.error(403, "ORIGIN_NOT_ALLOWED", "This request origin is not allowed"));
            return;
        }
        chain.doFilter(request, response);
    }
}
