package com.chess.backend.features.user.repository;

import com.chess.backend.features.user.domain.UserDraft;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDraftRepository extends JpaRepository<UserDraft, Long> {
    List<UserDraft> findAllByUserIdOrderByFeatureKey(long userId);
    Optional<UserDraft> findByUserIdAndFeatureKey(long userId, String featureKey);
    void deleteByUserIdAndFeatureKey(long userId, String featureKey);
}
