package com.chess.backend.social;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@Service
public class PresenceService {

    private final ConcurrentHashMap<Long, Set<String>> sessions = new ConcurrentHashMap<>();
    private final FriendService friendService;
    private final SimpMessagingTemplate messaging;

    public PresenceService(FriendService friendService, SimpMessagingTemplate messaging) {
        this.friendService = friendService;
        this.messaging = messaging;
    }

    @EventListener
    public void connected(SessionConnectedEvent event) {
        if (event.getUser() == null) return;
        long userId = parseUserId(event.getUser().getName());
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        boolean first = sessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
        if (first) broadcast(userId, "ONLINE");
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        if (event.getUser() == null) return;
        long userId = parseUserId(event.getUser().getName());
        Set<String> userSessions = sessions.get(userId);
        if (userSessions != null && userSessions.remove(event.getSessionId()) && userSessions.isEmpty()) {
            sessions.remove(userId, userSessions);
            broadcast(userId, "OFFLINE");
        }
    }

    public String status(long userId) {
        return sessions.containsKey(userId) ? "ONLINE" : "OFFLINE";
    }

    private void broadcast(long userId, String status) {
        PresenceEvent event = new PresenceEvent(userId, status);
        friendService.friendIds(userId).forEach(friendId ->
                messaging.convertAndSendToUser(Long.toString(friendId), "/queue/presence", event));
    }

    private static long parseUserId(String value) {
        return Long.parseLong(value);
    }

    public record PresenceEvent(long userId, String status) { }
}
