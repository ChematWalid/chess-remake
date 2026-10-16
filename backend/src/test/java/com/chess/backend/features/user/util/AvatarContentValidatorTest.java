package com.chess.backend.features.user.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.chess.backend.features.user.exception.AvatarContentMismatchException;
import com.chess.backend.features.user.exception.AvatarUploadMissingException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AvatarContentValidatorTest {
    private final AvatarContentValidator validator = new AvatarContentValidator();

    @Test
    void validatesContentAndReturnsAnImmutableSnapshot() {
        byte[] content = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", content);

        ValidatedAvatar avatar = validator.validate(file, 1024);
        byte[] returnedContent = avatar.content();
        returnedContent[0] = 0;

        assertEquals("image/png", avatar.mediaType());
        assertArrayEquals(content, avatar.content());
    }

    @Test
    void rejectsContentThatDoesNotMatchItsDeclaredMediaType() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[8]);

        assertThrows(AvatarContentMismatchException.class, () -> validator.validate(file, 1024));
    }

    @Test
    void rejectsMissingUploadWithoutDereferencingNull() {
        assertThrows(AvatarUploadMissingException.class, () -> validator.validate(null, 1024));
    }
}
