package com.chess.backend.features.user.validation;
import com.chess.backend.features.user.validation.UsernameValidator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UsernameValidatorTest {
    private final UsernameValidator validator = new UsernameValidator();

    @Test
    void acceptsLettersDigitsAndUnderscoresWithinBounds() {
        assertTrue(validator.isValid("chess_player7", null));
        assertTrue(validator.isValid("abc", null));
    }

    @Test
    void rejectsUnsupportedCharactersAndInvalidLengths() {
        assertFalse(validator.isValid("ab", null));
        assertFalse(validator.isValid("two words", null));
        assertFalse(validator.isValid("player-name", null));
    }
}
