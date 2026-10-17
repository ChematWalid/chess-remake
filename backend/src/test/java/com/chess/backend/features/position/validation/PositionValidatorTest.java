package com.chess.backend.features.position.validation;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class PositionValidatorTest {
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    void acceptsAPlayableStandardPositionAsCustomSetup() {
        assertNull(PositionValidator.validate(START_FEN, List.of(), List.of()));
    }

    @Test
    void rejectsPositionsWithoutBothKings() {
        assertNotNull(PositionValidator.validate("8/8/8/8/8/8/4K3/8 w - - 0 1", List.of(), List.of()));
    }

    @Test
    void rejectsReplayDataThatDoesNotEndAtTheSelectedFen() {
        String afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        assertNotNull(PositionValidator.validate(START_FEN, List.of("e4"), List.of(START_FEN, afterE4)));
        assertNull(PositionValidator.validate(afterE4, List.of("e4"), List.of(START_FEN, afterE4)));
    }
}
