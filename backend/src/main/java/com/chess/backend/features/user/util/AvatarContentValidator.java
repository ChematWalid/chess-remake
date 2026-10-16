package com.chess.backend.features.user.util;

import com.chess.backend.features.user.exception.AvatarContentMismatchException;
import com.chess.backend.features.user.exception.AvatarReadException;
import com.chess.backend.features.user.exception.AvatarUploadEmptyException;
import com.chess.backend.features.user.exception.AvatarUploadTooLargeException;
import com.chess.backend.features.user.exception.AvatarUploadMissingException;
import com.chess.backend.features.user.exception.UnsupportedAvatarMediaTypeException;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AvatarContentValidator {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    public ValidatedAvatar validate(MultipartFile file, long maximumSize) {
        if (file == null) {
            throw new AvatarUploadMissingException("Avatar file is required");
        }
        if (file.isEmpty()) {
            throw new AvatarUploadEmptyException("Avatar file is empty");
        }
        if (file.getSize() > maximumSize) {
            throw new AvatarUploadTooLargeException("Avatar exceeds the maximum size");
        }
        String mediaType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(mediaType)) {
            throw new UnsupportedAvatarMediaTypeException("Avatar must be PNG, JPEG, or WebP");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new AvatarReadException("Could not read avatar upload");
        }
        if (!matchesSignature(content, mediaType)) {
            throw new AvatarContentMismatchException("Avatar content does not match its image type");
        }
        return new ValidatedAvatar(content, mediaType);
    }

    private boolean matchesSignature(byte[] content, String mediaType) {
        return switch (mediaType) {
            case "image/png" -> content.length >= 8
                    && unsigned(content[0]) == 137
                    && content[1] == 80 && content[2] == 78 && content[3] == 71;
            case "image/jpeg" -> content.length >= 3
                    && unsigned(content[0]) == 255
                    && unsigned(content[1]) == 216
                    && unsigned(content[2]) == 255;
            case "image/webp" -> content.length >= 12
                    && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                    && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
            default -> false;
        };
    }

    private int unsigned(byte value) {
        return value & 255;
    }
}
