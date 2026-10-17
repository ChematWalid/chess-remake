package com.chess.backend.features.user.repository;

import com.chess.backend.features.user.domain.UserSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
    Optional<UserSettings> findByUserId(long userId);
}
