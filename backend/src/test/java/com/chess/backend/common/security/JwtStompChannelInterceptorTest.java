package com.chess.backend.common.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class JwtStompChannelInterceptorTest {
    private final JwtStompChannelInterceptor interceptor = new JwtStompChannelInterceptor(null);
    private final MessageChannel channel = null;

    @Test
    void rejectsClientSendToBrokerDestinations() {
        Message<?> message = message(StompCommand.SEND, "/topic/presence");

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void allowsClientSendToApplicationHandlers() {
        Message<?> message = message(StompCommand.SEND, "/app/challenge.send");

        assertNotNull(interceptor.preSend(message, channel));
    }

    private static Message<?> message(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
