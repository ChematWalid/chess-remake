package com.chess.backend;

import com.chess.backend.common.config.AppProperties;
import com.chess.backend.common.config.StompRelayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, StompRelayProperties.class})
public class ChessBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChessBackendApplication.class, args);
    }
}
