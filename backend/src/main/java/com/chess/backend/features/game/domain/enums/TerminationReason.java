package com.chess.backend.features.game.domain.enums;

public enum TerminationReason {
    CHECKMATE,
    RESIGNATION,
    TIMEOUT,
    STALEMATE,
    DRAW_AGREED,
    INSUFFICIENT_MATERIAL,
    FIFTY_MOVE_RULE,
    THREEFOLD_REPETITION,
    ABANDONED,
    UNKNOWN
}
