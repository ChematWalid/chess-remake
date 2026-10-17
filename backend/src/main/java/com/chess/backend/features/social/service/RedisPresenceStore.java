package com.chess.backend.features.social.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisPresenceStore {

    private static final Duration SESSION_LEASE = Duration.ofSeconds(45);
    private static final String KEY_PREFIX = "chess:presence:sessions:";
    private static final DefaultRedisScript<Long> UPSERT_SESSION = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local expires = tonumber(ARGV[2])
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now)
            redis.call('ZADD', key, expires, ARGV[3])
            redis.call('EXPIRE', key, ARGV[4])
            return redis.call('ZCARD', key)
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_SESSION = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            redis.call('ZREM', key, ARGV[2])
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now)
            local count = redis.call('ZCARD', key)
            if count == 0 then redis.call('DEL', key) end
            return count
            """, Long.class);
    private static final DefaultRedisScript<Long> SESSION_COUNT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            redis.call('ZREMRANGEBYSCORE', key, '-inf', tonumber(ARGV[1]))
            local count = redis.call('ZCARD', key)
            if count == 0 then redis.call('DEL', key) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisPresenceStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public boolean addOrRefresh(long userId, String sessionId) {
        long now = clock.millis();
        Long count = redis.execute(UPSERT_SESSION, List.of(key(userId)), Long.toString(now),
                Long.toString(now + SESSION_LEASE.toMillis()), sessionId,
                Long.toString(SESSION_LEASE.plusSeconds(15).toSeconds()));
        return count != null && count == 1;
    }

    public boolean remove(long userId, String sessionId) {
        Long count = redis.execute(REMOVE_SESSION, List.of(key(userId)), Long.toString(clock.millis()), sessionId);
        return count != null && count == 0;
    }

    public boolean isOnline(long userId) {
        Long count = redis.execute(SESSION_COUNT, List.of(key(userId)), Long.toString(clock.millis()));
        return count != null && count > 0;
    }

    private static String key(long userId) {
        return KEY_PREFIX + userId;
    }
}
