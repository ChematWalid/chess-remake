package com.chess.backend;

import com.chess.backend.common.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class ChessBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChessBackendApplication.class, args);
    }
}
