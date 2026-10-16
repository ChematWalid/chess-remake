package com.chess.backend.features.game.event;

public record GameRecordedEvent(long gameId, long whitePlayerId, long blackPlayerId) {
}
