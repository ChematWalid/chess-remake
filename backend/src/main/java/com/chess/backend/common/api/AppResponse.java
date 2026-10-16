package com.chess.backend.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppResponse<T>(boolean success, Integer code, String errorCode, String message, T data,
                             Map<String, String> errors, String traceId) {
    public static <T> AppResponse<T> success(T data) { return new AppResponse<>(true, null, null, "Success", data, null, null); }
    public static <T> AppResponse<T> success(T data, String message) { return new AppResponse<>(true, null, null, message, data, null, null); }
    public static <T> AppResponse<T> error(int code, String errorCode, String message) {
        return new AppResponse<>(false, code, errorCode, message, null, null, null);
    }
    public static <T> AppResponse<T> validation(int code, Map<String, String> errors) {
        return new AppResponse<>(false, code, "VALIDATION_FAILED", "Validation failed", null, errors, null);
    }
}
