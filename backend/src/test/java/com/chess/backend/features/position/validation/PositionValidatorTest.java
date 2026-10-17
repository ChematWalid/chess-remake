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
    void rejectsPositionsTheEngineWouldImmediatelyEnd() {
        assertNotNull(PositionValidator.validate("4k3/8/8/8/8/8/8/4K3 w - - 0 1", List.of(), List.of()));
        assertNotNull(PositionValidator.validate("4k3/4R3/8/8/8/8/8/4K3 w - - 0 1", List.of(), List.of()));
        assertNotNull(PositionValidator.validate(START_FEN.replace(" 0 1", " 100 1"), List.of(), List.of()));
        assertNotNull(PositionValidator.validate("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1", List.of(), List.of()));
        assertNotNull(PositionValidator.validate("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1", List.of(), List.of()));
    }

    @Test
    void rejectsReplayDataThatDoesNotEndAtTheSelectedFen() {
        String afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        assertNotNull(PositionValidator.validate(START_FEN, List.of("e4"), List.of(START_FEN, afterE4)));
        assertNull(PositionValidator.validate(afterE4, List.of("e4"), List.of(START_FEN, afterE4)));
    }

    @Test
    void rejectsSetupMovesThatDoNotProduceTheirFenSnapshots() {
        String afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        assertNotNull(PositionValidator.validate(afterE4, List.of("d4"), List.of(START_FEN, afterE4)));
    }

    @Test
    void rejectsCoordinateNotationThatTheEngineDoesNotAcceptAsSan() {
        String afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        assertNotNull(PositionValidator.validate(afterE4, List.of("e2e4"), List.of(START_FEN, afterE4)));
    }
}
