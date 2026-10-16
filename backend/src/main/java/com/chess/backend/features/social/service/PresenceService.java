package com.chess.backend.features.social.service;

import com.chess.backend.features.social.dto.PresenceEvent;
import com.chess.backend.features.social.domain.enums.PresenceStatus;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.user.service.UserDirectory;
import com.chess.backend.common.service.UserMessagePublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@Service
public class PresenceService {

    private final ConcurrentHashMap<Long, Set<String>> sessions = new ConcurrentHashMap<>();
    private final Set<Long> inGame = ConcurrentHashMap.newKeySet();
    private final FriendService friendService;
    private final UserDirectory users;
    private final UserMessagePublisher messages;

    public PresenceService(FriendService friendService, UserDirectory users, UserMessagePublisher messages) {
        this.friendService = friendService;
        this.users = users;
        this.messages = messages;
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
        AtomicBoolean becameOnline = new AtomicBoolean();
        sessions.compute(userId, (ignored, userSessions) -> {
            Set<String> updatedSessions = userSessions == null ? ConcurrentHashMap.newKeySet() : userSessions;
            if (updatedSessions.add(sessionId) && updatedSessions.size() == 1) {
                becameOnline.set(true);
            }
            return updatedSessions;
        });
        if (becameOnline.get()) {
            broadcast(userId, status(userId));
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
        AtomicBoolean becameOffline = new AtomicBoolean();
        sessions.computeIfPresent(userId, (ignored, userSessions) -> {
            userSessions.remove(event.getSessionId());
            if (userSessions.isEmpty()) {
                becameOffline.set(true);
                return null;
            }
            return userSessions;
        });
        if (becameOffline.get()) {
            broadcast(userId, status(userId));
        }
    }

    public PresenceStatus status(long userId) {
        if (inGame.contains(userId)) {
            return PresenceStatus.IN_GAME;
        }
        return sessions.containsKey(userId) ? PresenceStatus.ONLINE : PresenceStatus.OFFLINE;
    }

    public void gameStarted(long firstPlayerId, long secondPlayerId) {
        updateGameStatus(firstPlayerId, true);
        updateGameStatus(secondPlayerId, true);
    }

    public void gameCompleted(long firstPlayerId, long secondPlayerId) {
        updateGameStatus(firstPlayerId, false);
        updateGameStatus(secondPlayerId, false);
    }

    private void updateGameStatus(long userId, boolean playing) {
        boolean changed = playing ? inGame.add(userId) : inGame.remove(userId);
        if (changed) {
            broadcast(userId, status(userId));
        }
    }

    private void broadcast(long userId, PresenceStatus status) {
        users.findById(userId).map(User::getPublicId).ifPresent(publicUserId -> {
            PresenceEvent event = new PresenceEvent(publicUserId, status);
            friendService.friendIds(userId).forEach(friendId ->
                    messages.publish(friendId, "/queue/presence", event));
        });
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
