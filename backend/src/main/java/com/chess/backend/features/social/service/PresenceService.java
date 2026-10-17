package com.chess.backend.features.social.service;

import com.chess.backend.common.service.UserMessagePublisher;
import com.chess.backend.features.challenge.domain.GameChallenge;
import com.chess.backend.features.challenge.domain.enums.ChallengeStatus;
import com.chess.backend.features.challenge.repository.GameChallengeRepository;
import com.chess.backend.features.social.domain.enums.PresenceStatus;
import com.chess.backend.features.social.dto.PresenceEvent;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.user.service.UserDirectory;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Service
public class PresenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PresenceService.class);
    private final Map<String, Long> localSessions = new ConcurrentHashMap<>();
    private final FriendService friendService;
    private final UserDirectory users;
    private final UserMessagePublisher messages;
    private final GameChallengeRepository challenges;
    private final RedisPresenceStore presenceStore;

    public PresenceService(FriendService friendService, UserDirectory users, UserMessagePublisher messages,
                           GameChallengeRepository challenges, RedisPresenceStore presenceStore) {
        this.friendService = friendService;
        this.users = users;
        this.messages = messages;
        this.challenges = challenges;
        this.presenceStore = presenceStore;
    }

    @EventListener
    public void connected(SessionConnectedEvent event) {
        if (event == null || event.getUser() == null) {
            return;
        }
        Long userId = parseUserId(event.getUser().getName());
        if (userId == null) {
            return;
        }
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        localSessions.put(sessionId, userId);
        try {
            if (presenceStore.addOrRefresh(userId, sessionId)) {
                broadcast(userId, status(userId));
            }
        } catch (DataAccessException exception) {
            LOGGER.warn("Shared presence is unavailable while a user connects");
            broadcast(userId, PresenceStatus.UNKNOWN);
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        if (event == null || event.getUser() == null || event.getSessionId() == null) {
            return;
        }
        Long userId = parseUserId(event.getUser().getName());
        if (userId == null) {
            return;
        }
        localSessions.remove(event.getSessionId());
        try {
            if (presenceStore.remove(userId, event.getSessionId())) {
                broadcast(userId, status(userId));
            }
        } catch (DataAccessException exception) {
            LOGGER.warn("Shared presence is unavailable while a user disconnects");
            broadcast(userId, PresenceStatus.UNKNOWN);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void refreshLocalSessions() {
        localSessions.forEach((sessionId, userId) -> {
            try {
                presenceStore.addOrRefresh(userId, sessionId);
            } catch (DataAccessException exception) {
                LOGGER.warn("Shared presence refresh failed");
            }
        });
    }

    public PresenceStatus status(long userId) {
        return statuses(Set.of(userId)).getOrDefault(userId, PresenceStatus.UNKNOWN);
    }

    public Map<Long, PresenceStatus> statuses(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> uniqueIds = new HashSet<>(userIds);
        Set<Long> inGame = new HashSet<>();
        try {
            List<Long> ids = List.copyOf(uniqueIds);
            for (GameChallenge challenge : challenges.findActiveForParticipants(ChallengeStatus.ACCEPTED, ids)) {
                inGame.add(challenge.getChallenger().getId());
                inGame.add(challenge.getChallenged().getId());
            }
        } catch (DataAccessException exception) {
            LOGGER.warn("Game status is unavailable while resolving presence");
            return unknownStatuses(uniqueIds);
        }

        Map<Long, PresenceStatus> result = new HashMap<>();
        for (Long userId : uniqueIds) {
            if (inGame.contains(userId)) {
                result.put(userId, PresenceStatus.IN_GAME);
                continue;
            }
            try {
                result.put(userId, presenceStore.isOnline(userId) ? PresenceStatus.ONLINE : PresenceStatus.OFFLINE);
            } catch (DataAccessException exception) {
                result.put(userId, PresenceStatus.UNKNOWN);
            }
        }
        return result;
    }

    public void gameStarted(long firstPlayerId, long secondPlayerId) {
        broadcast(firstPlayerId, status(firstPlayerId));
        broadcast(secondPlayerId, status(secondPlayerId));
    }

    public void gameCompleted(long firstPlayerId, long secondPlayerId) {
        broadcast(firstPlayerId, status(firstPlayerId));
        broadcast(secondPlayerId, status(secondPlayerId));
    }

    private void broadcast(long userId, PresenceStatus status) {
        users.findById(userId).map(User::getPublicId).ifPresent(publicUserId -> {
            PresenceEvent event = new PresenceEvent(publicUserId, status);
            friendService.friendIds(userId).forEach(friendId ->
                    messages.publish(friendId, "/topic/presence", event));
        });
    }

    private static Map<Long, PresenceStatus> unknownStatuses(Set<Long> userIds) {
        Map<Long, PresenceStatus> result = new HashMap<>();
        userIds.forEach(userId -> result.put(userId, PresenceStatus.UNKNOWN));
        return result;
    }

    private static Long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
