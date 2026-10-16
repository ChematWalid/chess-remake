package com.chess.backend.features.user.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public record StoredFile(InputStream input, String contentType, long size) implements Closeable {
    @Override
    public void close() throws IOException {
        input.close();
    }
}
