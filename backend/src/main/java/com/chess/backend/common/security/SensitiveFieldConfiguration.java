package com.chess.backend.common.security;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SensitiveFieldConfiguration {
    @Bean
    Module sensitiveFieldModule() {
        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new SensitiveFieldSerializerModifier());
        return module;
    }
}
