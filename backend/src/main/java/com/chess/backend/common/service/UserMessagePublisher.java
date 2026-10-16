package com.chess.backend.common.service;

import com.chess.backend.common.api.UserDestinationMessage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class UserMessagePublisher {
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessageSendingOperations messaging;

    public UserMessagePublisher(ApplicationEventPublisher eventPublisher, SimpMessageSendingOperations messaging) {
        this.eventPublisher = eventPublisher;
        this.messaging = messaging;
    }

    public void publish(long recipientUserId, String destination, Object payload) {
        UserDestinationMessage message = new UserDestinationMessage(recipientUserId, destination, payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(message);
            return;
        }
        send(message);
    }

    public void send(UserDestinationMessage message) {
        messaging.convertAndSendToUser(Long.toString(message.recipientUserId()), message.destination(), message.payload());
    }
}
