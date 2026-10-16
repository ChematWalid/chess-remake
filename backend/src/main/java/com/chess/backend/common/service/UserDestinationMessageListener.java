package com.chess.backend.common.service;

import com.chess.backend.common.api.UserDestinationMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserDestinationMessageListener {
    private final UserMessagePublisher publisher;

    public UserDestinationMessageListener(UserMessagePublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener
    public void sendAfterCommit(UserDestinationMessage message) {
        publisher.send(message);
    }
}
