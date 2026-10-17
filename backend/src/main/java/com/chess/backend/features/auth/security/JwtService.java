package com.chess.backend.features.auth.security;


import com.chess.backend.common.security.ChessPrincipal;
import com.chess.backend.common.config.AppProperties;
import com.chess.backend.common.domain.enums.UserRole;
import com.chess.backend.features.challenge.domain.enums.GameSide;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final AppProperties properties;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String createAccessToken(long userId, String username, UserRole role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .id(UUID.randomUUID().toString())
                .claim("username", username)
                .claim("roles", List.of(role.name()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(accessKey())
                .compact();
    }

    public ChessPrincipal parseAccessToken(String token) {
        var claims = Jwts.parser().verifyWith(accessKey()).clock(() -> Date.from(clock.instant())).build()
                .parseSignedClaims(token).getPayload();
        return new ChessPrincipal(Long.parseLong(claims.getSubject()),
                claims.get("username", String.class),
                UserRole.valueOf(claims.get("roles", List.class).getFirst().toString()));
    }

    public String createRoomToken(String roomCode, long userId, String displayName, GameSide color,
                                  Integer initialSeconds, Integer incrementSeconds) {
        return createRoomToken(roomCode, userId, displayName, color, initialSeconds, incrementSeconds, null, List.of(), List.of());
    }

    public String createRoomToken(String roomCode, long userId, String displayName, GameSide color,
                                  Integer initialSeconds, Integer incrementSeconds, String initialFen,
                                  List<String> setupMoves, List<String> setupFens) {
        Instant now = clock.instant();
        Map<String, Object> claims = new HashMap<>();
        claims.put("room_code", roomCode);
        claims.put("user_id", Long.toString(userId));
        claims.put("display_name", displayName);
        claims.put("color", color.name().toLowerCase(Locale.ROOT));
        claims.put("initial_seconds", initialSeconds == null ? 0 : initialSeconds);
        claims.put("increment_seconds", incrementSeconds == null ? 0 : incrementSeconds);
        if (initialFen != null) claims.put("initial_fen", initialFen);
        claims.put("setup_moves", setupMoves == null ? List.of() : setupMoves);
        claims.put("setup_fens", setupFens == null ? List.of() : setupFens);
        return Jwts.builder().claims(claims).id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(properties.roomTokenTtl())))
                .signWith(engineKey(), Jwts.SIG.HS256).compact();
    }

    private SecretKey accessKey() {
        return Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey engineKey() {
        return Keys.hmacShaKeyFor(properties.engineJwtSecret().getBytes(StandardCharsets.UTF_8));
    }
}
