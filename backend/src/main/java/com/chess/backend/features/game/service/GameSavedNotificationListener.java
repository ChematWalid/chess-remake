package com.chess.backend.features.game.service;

import com.chess.backend.features.game.event.GameRecordedEvent;
import com.chess.backend.features.social.domain.enums.NotificationType;
import com.chess.backend.features.social.dto.UserNotification;
import java.util.List;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class GameSavedNotificationListener {
    private final SimpMessageSendingOperations messaging;

    public GameSavedNotificationListener(SimpMessageSendingOperations messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener
    public void gameRecorded(GameRecordedEvent event) {
        UserNotification notification = new UserNotification(NotificationType.GAME_SAVED,
                Long.toString(event.gameId()));
        List.of(event.whitePlayerId(), event.blackPlayerId()).forEach(userId ->
                messaging.convertAndSendToUser(Long.toString(userId), "/queue/notifications", notification));
    }
}
