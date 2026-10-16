package com.chess.backend.features.user.service;

import com.chess.backend.features.user.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;

public final class AvatarReplacementSynchronization implements TransactionSynchronization {
    private static final Logger LOGGER = LoggerFactory.getLogger(AvatarReplacementSynchronization.class);
    private final FileStorage storage;
    private final String newKey;
    private final String previousKey;

    public AvatarReplacementSynchronization(FileStorage storage, String newKey, String previousKey) {
        this.storage = storage;
        this.newKey = newKey;
        this.previousKey = previousKey;
    }

    @Override
    public void afterCommit() {
        deleteQuietly(previousKey);
    }

    @Override
    public void afterCompletion(int status) {
        if (status != STATUS_COMMITTED) {
            deleteQuietly(newKey);
        }
    }

    private void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not clean up avatar object {}", key, exception);
        }
    }
}
