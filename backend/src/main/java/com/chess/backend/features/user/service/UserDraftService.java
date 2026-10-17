package com.chess.backend.features.user.service;

import com.chess.backend.features.user.domain.UserDraft;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.user.dto.UserDraftView;
import com.chess.backend.features.user.exception.InvalidUserDraftException;
import com.chess.backend.features.user.exception.UserDraftLimitExceededException;
import com.chess.backend.features.user.repository.UserDraftRepository;
import com.chess.backend.features.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDraftService {
    private static final int MAX_PAYLOAD_BYTES = 65_536;
    private static final int MAX_DRAFTS_PER_USER = 20;

    private final UserDraftRepository drafts;
    private final UserRepository users;
    private final Clock clock;

    public UserDraftService(UserDraftRepository drafts, UserRepository users, Clock clock) {
        this.drafts = drafts;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserDraftView> getAll(long userId) {
        return drafts.findAllByUserIdOrderByFeatureKey(userId).stream()
                .map(UserDraftService::view)
                .toList();
    }

    @Transactional
    public UserDraftView save(long userId, String featureKey, JsonNode payload, Instant editedAt) {
        if (payload == null || payload.isNull()
                || payload.toString().getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new InvalidUserDraftException("A draft must be present and smaller than 64 KB.");
        }
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new InvalidUserDraftException("The account for this draft no longer exists."));
        Instant effectiveEditedAt = editedAt == null ? clock.instant() : editedAt;
        UserDraft draft = drafts.findByUserIdAndFeatureKey(userId, featureKey).orElse(null);
        if (draft != null && draft.getEditedAt().isAfter(effectiveEditedAt)) return view(draft);
        if (draft == null && drafts.countByUserId(userId) >= MAX_DRAFTS_PER_USER) {
            throw new UserDraftLimitExceededException();
        }
        if (draft == null) draft = new UserDraft(user, featureKey, payload, effectiveEditedAt);
        else draft.update(payload, effectiveEditedAt);
        return view(drafts.saveAndFlush(draft));
    }

    @Transactional
    public void delete(long userId, String featureKey) {
        drafts.deleteByUserIdAndFeatureKey(userId, featureKey);
    }

    private static UserDraftView view(UserDraft draft) {
        return new UserDraftView(draft.getFeatureKey(), draft.getPayload(), draft.getUpdatedAt(), draft.getEditedAt());
    }
}
