package com.chess.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.chess.backend.features.challenge.exception.InvalidTimeControlException;
import org.junit.jupiter.api.Test;

class StompExceptionHandlerTest {
    private final StompExceptionHandler handler = new StompExceptionHandler();

    @Test
    void returnsTypedApplicationErrorForUserQueue() {
        var event = handler.handleApplicationException(new InvalidTimeControlException("Invalid clock"));

        assertThat(event.errorCode()).isEqualTo("INVALID_TIME_CONTROL");
        assertThat(event.message()).isEqualTo("Invalid clock");
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        var event = handler.handleUnexpectedException(new IllegalStateException("private details"));

        assertThat(event.errorCode()).isEqualTo("MESSAGE_FAILED");
        assertThat(event.message()).doesNotContain("private details");
    }
}
