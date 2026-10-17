package com.chess.backend.common.config;

import com.chess.backend.common.security.JwtStompChannelInterceptor;
import java.util.Arrays;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties properties;
    private final JwtStompChannelInterceptor stompChannelInterceptor;
    private final StompRelayProperties relayProperties;

    public WebSocketConfig(AppProperties properties, JwtStompChannelInterceptor stompChannelInterceptor,
                           StompRelayProperties relayProperties) {
        this.properties = properties;
        this.stompChannelInterceptor = stompChannelInterceptor;
        this.relayProperties = relayProperties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableStompBrokerRelay("/topic")
                .setRelayHost(relayProperties.host())
                .setRelayPort(relayProperties.port())
                .setClientLogin(relayProperties.login())
                .setClientPasscode(relayProperties.passcode())
                .setSystemLogin(relayProperties.login())
                .setSystemPasscode(relayProperties.passcode())
                .setUserDestinationBroadcast("/topic/unresolved-user-destination")
                .setUserRegistryBroadcast("/topic/simp-user-registry");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(properties.corsOrigins().split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toArray(String[]::new);
        registry.addEndpoint("/ws").setAllowedOrigins(origins).withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompChannelInterceptor);
    }

}
