package com.chess.backend.common.config;

import com.chess.backend.common.api.StompErrorEvent;
import com.chess.backend.common.exception.AppException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class StompExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StompExceptionHandler.class);

    @MessageExceptionHandler(AppException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    StompErrorEvent handleApplicationException(AppException exception) {
        return new StompErrorEvent(exception.errorCode(), exception.getMessage());
    }

    @MessageExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @SendToUser(value = "/queue/errors", broadcast = false)
    StompErrorEvent handleValidationException(Exception exception) {
        return new StompErrorEvent("VALIDATION_FAILED", "One or more message values are invalid");
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    StompErrorEvent handleUnexpectedException(Exception exception) {
        LOGGER.error("Unhandled STOMP message failure", exception);
        return new StompErrorEvent("MESSAGE_FAILED", "The requested action could not be completed");
    }
}
