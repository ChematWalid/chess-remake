package com.chess.backend.features.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.user.domain.UserDraft;
import com.chess.backend.features.user.exception.InvalidUserDraftException;
import com.chess.backend.features.user.repository.UserDraftRepository;
import com.chess.backend.features.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserDraftServiceTest {
    private final UserDraftRepository drafts = mock(UserDraftRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserDraftService service = new UserDraftService(drafts, users);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void storesDraftsByAccountAndFeatureAndReplacesExistingPayload() {
        User user = mock(User.class);
        when(users.getReferenceById(42L)).thenReturn(user);
        when(drafts.save(any(UserDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var first = service.save(42L, "position-setup", mapper.valueToTree("first"));

        assertEquals("position-setup", first.featureKey());
        assertEquals("first", first.payload().textValue());
        verify(drafts).save(any(UserDraft.class));

        UserDraft existing = new UserDraft(user, "position-setup", mapper.valueToTree("old"));
        when(drafts.findByUserIdAndFeatureKey(42L, "position-setup"))
                .thenReturn(Optional.of(existing));

        var updated = service.save(42L, "position-setup", mapper.valueToTree("latest"));

        assertEquals("latest", updated.payload().textValue());
        assertEquals("latest", existing.getPayload().textValue());
    }

    @Test
    void rejectsDraftPayloadsLargerThanTheStorageLimit() {
        String oversized = "x".repeat(65_536);

        assertThrows(
                InvalidUserDraftException.class,
                () -> service.save(42L, "profile-edit", mapper.valueToTree(oversized)));
        verify(drafts, never()).save(any(UserDraft.class));
    }

    @Test
    void deletesOnlyTheCurrentUsersMatchingFeatureDraft() {
        service.delete(42L, "player-search");

        verify(drafts).deleteByUserIdAndFeatureKey(42L, "player-search");
    }
}
