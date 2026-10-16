package com.chess.backend.features.user.service;

import com.chess.backend.features.user.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AvatarStorageService {
    private final FileStorage storage;

    public AvatarStorageService(FileStorage storage) {
        this.storage = storage;
    }

    public String replace(byte[] content, String mediaType, String previousKey) {
        Objects.requireNonNull(content, "Avatar content is required");
        Objects.requireNonNull(mediaType, "Avatar media type is required");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Avatar replacement requires an active transaction");
        }
        String newKey = storage.store("avatars", new ByteArrayInputStream(content), content.length, mediaType);
        if (newKey == null || newKey.isBlank()) {
            throw new IllegalStateException("Object storage returned an empty avatar key");
        }
        TransactionSynchronizationManager.registerSynchronization(
                new AvatarReplacementSynchronization(storage, newKey, previousKey));
        return newKey;
    }
}
