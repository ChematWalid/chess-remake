package com.chess.backend.features.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OAuthClientConfiguration {
    @Bean
    public RestClient oauthRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
