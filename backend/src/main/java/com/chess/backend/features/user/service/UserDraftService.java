package com.chess.backend.features.user.service;

import com.chess.backend.features.user.domain.UserDraft;
import com.chess.backend.features.user.dto.UserDraftView;
import com.chess.backend.features.user.exception.InvalidUserDraftException;
import com.chess.backend.features.user.repository.UserDraftRepository;
import com.chess.backend.features.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDraftService {
    private static final int MAX_PAYLOAD_BYTES = 65_536;

    private final UserDraftRepository drafts;
    private final UserRepository users;

    public UserDraftService(UserDraftRepository drafts, UserRepository users) {
        this.drafts = drafts;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<UserDraftView> getAll(long userId) {
        return drafts.findAllByUserIdOrderByFeatureKey(userId).stream()
                .map(UserDraftService::view)
                .toList();
    }

    @Transactional
    public UserDraftView save(long userId, String featureKey, JsonNode payload) {
        if (payload == null || payload.toString().getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new InvalidUserDraftException("A draft must be present and smaller than 64 KB.");
        }
        UserDraft draft = drafts.findByUserIdAndFeatureKey(userId, featureKey)
                .orElseGet(() -> new UserDraft(users.getReferenceById(userId), featureKey, payload));
        draft.update(payload);
        return view(drafts.save(draft));
    }

    @Transactional
    public void delete(long userId, String featureKey) {
        drafts.deleteByUserIdAndFeatureKey(userId, featureKey);
    }

    private static UserDraftView view(UserDraft draft) {
        return new UserDraftView(draft.getFeatureKey(), draft.getPayload(), draft.getUpdatedAt());
    }
}
