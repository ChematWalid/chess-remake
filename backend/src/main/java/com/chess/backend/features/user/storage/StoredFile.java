package com.chess.backend.features.user.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public record StoredFile(InputStream input, String contentType, long size) implements Closeable {
    public StoredFile {
        Objects.requireNonNull(input, "Stored file stream is required");
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
