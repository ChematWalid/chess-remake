package com.chess.backend.features.game.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chess.backend.features.game.event.GameRecordedEvent;
import com.chess.backend.features.social.dto.UserNotification;
import com.chess.backend.features.social.domain.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

class GameSavedNotificationListenerTest {
    @Test
    void notifiesBothPlayersWhenGameIsRecorded() {
        SimpMessageSendingOperations messaging = mock(SimpMessageSendingOperations.class);
        GameSavedNotificationListener listener = new GameSavedNotificationListener(messaging);

        listener.gameRecorded(new GameRecordedEvent(57, 11, 12));

        verify(messaging).convertAndSendToUser(eq("11"), eq("/topic/notifications"),
                argThat(payload -> isGameSavedNotification(payload, "57")));
        verify(messaging).convertAndSendToUser(eq("12"), eq("/topic/notifications"),
                argThat(payload -> isGameSavedNotification(payload, "57")));
    }

    private static boolean isGameSavedNotification(Object payload, String gameId) {
        return payload instanceof UserNotification notification
                && notification.type() == NotificationType.GAME_SAVED
                && gameId.equals(notification.subjectId());
    }
}
