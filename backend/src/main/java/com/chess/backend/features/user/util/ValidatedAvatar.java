package com.chess.backend.features.user.util;

import java.util.Objects;

public record ValidatedAvatar(byte[] content, String mediaType) {
    public ValidatedAvatar {
        content = Objects.requireNonNull(content, "Avatar content is required").clone();
        mediaType = Objects.requireNonNull(mediaType, "Avatar media type is required");
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
