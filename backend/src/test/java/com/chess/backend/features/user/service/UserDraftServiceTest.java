package com.chess.backend.features.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.user.domain.UserDraft;
import com.chess.backend.features.user.exception.InvalidUserDraftException;
import com.chess.backend.features.user.exception.UserDraftLimitExceededException;
import com.chess.backend.features.user.repository.UserDraftRepository;
import com.chess.backend.features.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UserDraftServiceTest {
    private final UserDraftRepository drafts = mock(UserDraftRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
    private final UserDraftService service = new UserDraftService(drafts, users, clock);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void storesDraftsByAccountAndFeatureAndReplacesExistingPayload() {
        User user = mock(User.class);
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(drafts.saveAndFlush(any(UserDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var first = service.save(42L, "position-setup", mapper.valueToTree("first"), null);

        assertEquals("position-setup", first.featureKey());
        assertEquals("first", first.payload().textValue());
        verify(drafts).saveAndFlush(any(UserDraft.class));

        UserDraft existing = new UserDraft(user, "position-setup", mapper.valueToTree("old"), clock.instant());
        when(drafts.findByUserIdAndFeatureKey(42L, "position-setup"))
                .thenReturn(Optional.of(existing));

        var updated = service.save(42L, "position-setup", mapper.valueToTree("latest"), clock.instant().plusSeconds(1));

        assertEquals("latest", updated.payload().textValue());
        assertEquals("latest", existing.getPayload().textValue());
    }

    @Test
    void rejectsDraftPayloadsLargerThanTheStorageLimit() {
        String oversized = "x".repeat(65_536);

        assertThrows(
                InvalidUserDraftException.class,
                () -> service.save(42L, "profile-edit", mapper.valueToTree(oversized), null));
        verify(drafts, never()).saveAndFlush(any(UserDraft.class));
    }

    @Test
    void rejectsJsonNullPayloads() {
        assertThrows(
                InvalidUserDraftException.class,
                () -> service.save(42L, "profile-edit", mapper.nullNode(), null));
        verifyNoMoreInteractions(users, drafts);
    }

    @Test
    void keepsTheNewestEditWhenAnOlderOfflineDraftArrives() {
        User user = mock(User.class);
        UserDraft newest = new UserDraft(user, "position-setup", mapper.valueToTree("new"),
                Instant.parse("2026-10-17T12:02:00Z"));
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(drafts.findByUserIdAndFeatureKey(42L, "position-setup")).thenReturn(Optional.of(newest));

        var result = service.save(42L, "position-setup", mapper.valueToTree("old"),
                Instant.parse("2026-10-17T12:01:00Z"));

        assertEquals("new", result.payload().textValue());
        assertEquals(Instant.parse("2026-10-17T12:02:00Z"), result.editedAt());
        verify(drafts, never()).saveAndFlush(any(UserDraft.class));
    }

    @Test
    void rejectsDraftsOverThePerAccountLimit() {
        User user = mock(User.class);
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(drafts.findByUserIdAndFeatureKey(42L, "new-feature")).thenReturn(Optional.empty());
        when(drafts.countByUserId(42L)).thenReturn(20L);

        assertThrows(
                UserDraftLimitExceededException.class,
                () -> service.save(42L, "new-feature", mapper.valueToTree("payload"), null));
        verify(drafts, never()).saveAndFlush(any(UserDraft.class));
    }

    @Test
    void deletesOnlyTheCurrentUsersMatchingFeatureDraft() {
        service.delete(42L, "player-search");

        verify(drafts).deleteByUserIdAndFeatureKey(42L, "player-search");
    }
}
