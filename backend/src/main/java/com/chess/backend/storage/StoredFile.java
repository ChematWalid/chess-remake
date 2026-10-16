package com.chess.backend.storage;

import java.io.InputStream;

public record StoredFile(InputStream input, String contentType, long size) implements AutoCloseable {
    @Override
    public void close() throws Exception {
        input.close();
    }
}
