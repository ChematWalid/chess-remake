package com.chess.backend.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import com.chess.backend.common.api.AppResponse;

public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public AuthRateLimitFilter(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || rule(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = rule(request.getRequestURI());
        CachedBodyRequest wrapped = new CachedBodyRequest(request);
        if (wrapped.body().length > 65_536) {
            response.setStatus(413);
            return;
        }
        try {
            String ip = clientAddress(request);
            if (!allow("ip:" + ip, rule.ipLimit(), rule.window())) {
                tooManyRequests(response, rule.window());
                return;
            }
            String identifier = identifier(wrapped, rule.identifierField());
            if (identifier != null && !allow("identifier:" + identifier, rule.identifierLimit(), rule.window())) {
                tooManyRequests(response, rule.window());
                return;
            }
        } catch (DataAccessException exception) {
            response.setStatus(503);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), AppResponse.error(503, "AUTH_RATE_LIMIT_UNAVAILABLE",
                    "Authentication is temporarily unavailable"));
            return;
        }
        chain.doFilter(wrapped, response);
    }

    private boolean allow(String identity, int limit, Duration window) {
        if (limit < 1) return true;
        String key = "chess:rate-limit:" + digest(identity);
        Long count = redis.execute(INCREMENT, List.of(key), Long.toString(window.toMillis()));
        if (count == null) {
            throw new DataAccessResourceFailureException("Redis did not return a rate limit count");
        }
        return count <= limit;
    }

    private String identifier(CachedBodyRequest request, String field) {
        if (field == null) return null;
        try {
            JsonNode json = mapper.readTree(request.body());
            JsonNode value = json == null ? null : json.get(field);
            if (value == null || !value.isTextual() || value.asText().isBlank()) return null;
            return field + ":" + value.asText().trim().toLowerCase(Locale.ROOT);
        } catch (IOException exception) {
            return null;
        }
    }

    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",", 2)[0].trim();
            if (!first.isBlank()) return first;
        }
        return request.getRemoteAddr();
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void tooManyRequests(HttpServletResponse response, Duration window) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(window.toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        new ObjectMapper().writeValue(response.getOutputStream(),
                AppResponse.error(429, "AUTH_RATE_LIMITED", "Too many attempts. Try again later"));
    }

    private static Rule rule(String uri) {
        return switch (uri) {
            case "/api/auth/login" -> new Rule(20, 8, Duration.ofMinutes(15), "identifier");
            case "/api/auth/register" -> new Rule(5, 0, Duration.ofHours(1), null);
            case "/api/auth/forgot-password" -> new Rule(15, 5, Duration.ofHours(1), "email");
            case "/api/auth/verify-email", "/api/auth/reset-password" ->
                    new Rule(15, 5, Duration.ofHours(1), "token");
            case "/api/auth/oauth/exchange" -> new Rule(20, 8, Duration.ofMinutes(15), "code");
            default -> null;
        };
    }

    private record Rule(int ipLimit, int identifierLimit, Duration window, String identifierField) { }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }
        private byte[] body() { return body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
                @Override public int read() { return input.read(); }
            };
        }
        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
