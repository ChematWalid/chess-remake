package com.chess.backend.common.security;

import com.chess.backend.common.domain.enums.UserRole;
import com.chess.backend.features.auth.security.JwtService;
import java.util.List;
import java.util.Set;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> ALLOWED_SEND_DESTINATIONS = Set.of(
            "/app/challenge.send", "/app/challenge.accept", "/app/challenge.decline", "/app/challenge.cancel");
    private static final String USER_QUEUE_PREFIX = "/user/queue/";
    private final JwtService jwtService;

    public JwtStompChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(message, accessor);
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            validateSubscription(message, accessor.getDestination());
        }
        if (StompCommand.SEND.equals(accessor.getCommand())) {
            validateSendDestination(message, accessor.getDestination());
        }
        return message;
    }

    private void authenticate(Message<?> message, StompHeaderAccessor accessor) {
        List<String> headers = accessor.getNativeHeader(AUTHORIZATION_HEADER);
        String authorization = headers == null || headers.isEmpty() ? null : headers.getFirst();
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException(message, "CONNECT requires bearer authorization");
        }
        ChessPrincipal principal = jwtService.parseAccessToken(authorization.substring(BEARER_PREFIX.length()));
        if (principal.role() != UserRole.ROLE_USER) {
            throw new MessageDeliveryException(message, "Account onboarding must be completed before connecting");
        }
        accessor.setUser(new StompUserPrincipal(principal.userId()));
    }

    private void validateSubscription(Message<?> message, String destination) {
        if (destination == null || !destination.startsWith(USER_QUEUE_PREFIX)) {
            throw new MessageDeliveryException(message, "Subscription destination is not allowed");
        }
    }

    private void validateSendDestination(Message<?> message, String destination) {
        if (destination == null || !ALLOWED_SEND_DESTINATIONS.contains(destination)) {
            throw new MessageDeliveryException(message, "Send destination is not allowed");
        }
    }
}
