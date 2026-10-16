package com.chess.backend.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import com.chess.backend.domain.enums.GameResult;

class UserTest {
    @Test
    void updatesChessCountersByResult() {
        User user = new User("player", "player@example.test", "hash", "Player");
        user.recordResult(GameResult.WIN);
        user.recordResult(GameResult.DRAW);
        user.recordResult(GameResult.LOSS);
        assertEquals(3, user.getTotalGames());
        assertEquals(1, user.getWins());
        assertEquals(1, user.getDraws());
        assertEquals(1, user.getLosses());
    }

}
