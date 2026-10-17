package com.chess.backend.features.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.chess.backend.features.game.domain.enums.GameResult;

class UserTest {
    @Test
    void updatesChessCountersByResult() {
        User user = new User("player", "player@example.test", "hash", "Player");
        assertNotNull(user.getPublicId());
        user.recordResult(GameResult.WIN);
        user.recordResult(GameResult.DRAW);
        user.recordResult(GameResult.LOSS);
        assertEquals(3, user.getTotalGames());
        assertEquals(1, user.getWins());
        assertEquals(1, user.getDraws());
        assertEquals(1, user.getLosses());
    }

    @Test
    void enablesMoveAndTurnSoundsForNewSettings() {
        UserSettings settings = new UserSettings(
                new User("player", "player@example.test", "hash", "Player"));

        assertTrue(settings.isGameSoundsEnabled());
        assertTrue(settings.isTurnSoundEnabled());
    }

}
