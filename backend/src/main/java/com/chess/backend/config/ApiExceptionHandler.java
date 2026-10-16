package com.chess.backend.config;

import com.chess.backend.api.AppResponse;
import com.chess.backend.exception.AppException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(AppException.class)
    ResponseEntity<AppResponse<Void>> handleApp(AppException exception) {
        return ResponseEntity.status(exception.status()).body(AppResponse.error(exception.status().value(), exception.errorCode(), exception.getMessage()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<AppResponse<Void>> handleConflict(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "CONFLICT", "The requested value conflicts with an existing record");
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<AppResponse<Void>> handleUnreadableRequest() {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body is invalid");
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<AppResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(field -> errors.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return ResponseEntity.badRequest().body(AppResponse.validation(HttpStatus.BAD_REQUEST.value(), errors));
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<AppResponse<Void>> handleTypeMismatch() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "A request parameter has an invalid value");
    }
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<AppResponse<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Required parameter is missing: " + exception.getParameterName());
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<AppResponse<Void>> handleMethodNotAllowed() {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "The HTTP method is not supported for this resource");
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<AppResponse<Void>> handleUnsupportedMediaType() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "The request media type is not supported");
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<AppResponse<Void>> handleUploadTooLarge() {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "The uploaded file exceeds the configured size limit");
    }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<AppResponse<Void>> handleAuthentication() { return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed"); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<AppResponse<Void>> handleAuthorization() { return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<AppResponse<Void>> handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled request failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }
    private ResponseEntity<AppResponse<Void>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(AppResponse.error(status.value(), code, message));
    }
}
