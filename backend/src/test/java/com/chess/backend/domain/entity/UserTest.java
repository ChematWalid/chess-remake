package com.chess.backend.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UserTest {
    @Test
    void updatesChessCountersByResult() {
        User user = new User("player", "player@example.test", "hash", "Player");
        user.recordResult("WIN");
        user.recordResult("DRAW");
        user.recordResult("LOSS");
        assertEquals(3, user.getTotalGames());
        assertEquals(1, user.getWins());
        assertEquals(1, user.getDraws());
        assertEquals(1, user.getLosses());
    }

    @Test
    void rejectsUnknownResult() {
        User user = new User("player", "player@example.test", "hash", "Player");
        assertThrows(IllegalArgumentException.class, () -> user.recordResult("VOID"));
    }
}
