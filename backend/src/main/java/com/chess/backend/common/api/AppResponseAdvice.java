package com.chess.backend.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class AppResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) { return true; }
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || body instanceof AppResponse<?> || body instanceof byte[] || body instanceof String
                || contentType.includes(MediaType.APPLICATION_OCTET_STREAM) || contentType.toString().contains("chess-pgn")) return body;
        int code = response instanceof ServletServerHttpResponse servlet ? servlet.getServletResponse().getStatus() : 200;
        return new AppResponse<>(true, code, null, "Success", body, null,
                request.getHeaders().getFirst("X-Correlation-ID"));
    }
}
