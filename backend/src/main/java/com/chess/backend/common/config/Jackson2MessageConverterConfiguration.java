package com.chess.backend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class Jackson2MessageConverterConfiguration implements WebMvcConfigurer {
    private final ObjectMapper objectMapper;

    public Jackson2MessageConverterConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, new MappingJackson2HttpMessageConverter(objectMapper) {
            @Override
            public boolean canWrite(Class<?> clazz, MediaType mediaType) {
                return clazz != byte[].class && super.canWrite(clazz, mediaType);
            }
        });
    }
}
