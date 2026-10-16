package com.chess.backend.common.config;

import com.chess.backend.common.security.ChessPrincipal;
import com.chess.backend.features.auth.security.JwtService;
import com.chess.backend.common.domain.enums.UserRole;
import java.security.Principal;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties properties;
    private final JwtService jwtService;

    public WebSocketConfig(AppProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(properties.corsOrigins().split(",")).withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> headers = accessor.getNativeHeader("Authorization");
                    String value = headers == null || headers.isEmpty() ? null : headers.getFirst();
                    if (value == null || !value.startsWith("Bearer ")) {
                        throw new MessageDeliveryException(message, "CONNECT requires bearer authorization");
                    }
                    ChessPrincipal principal = jwtService.parseAccessToken(value.substring(7));
                    if (principal.role() != UserRole.ROLE_USER) {
                        throw new MessageDeliveryException(message, "Account onboarding must be completed before connecting");
                    }
                    accessor.setUser(new UserPrincipal(principal.userId()));
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination == null || !destination.startsWith("/user/queue/")) {
                        throw new MessageDeliveryException(message, "Subscription destination is not allowed");
                    }
                }
                return message;
            }
        });
    }

    private record UserPrincipal(long id) implements Principal {
        @Override
        public String getName() { return Long.toString(id); }
    }
}
