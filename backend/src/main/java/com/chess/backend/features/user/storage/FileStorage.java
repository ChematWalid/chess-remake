package com.chess.backend.features.user.storage;

import java.io.InputStream;

public interface FileStorage {
    String store(String category, InputStream input, long size, String contentType);
    StoredFile retrieve(String key);
    void delete(String key);
}
