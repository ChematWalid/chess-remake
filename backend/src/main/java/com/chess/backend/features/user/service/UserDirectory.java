package com.chess.backend.features.user.service;

import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.user.domain.UserSettings;
import com.chess.backend.features.game.domain.enums.GameResult;
import com.chess.backend.features.user.repository.UserRepository;
import com.chess.backend.features.user.repository.UserSettingsRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDirectory {
    private final UserRepository users;
    private final UserSettingsRepository settings;

    public UserDirectory(UserRepository users, UserSettingsRepository settings) {
        this.users = users;
        this.settings = settings;
    }

    @Transactional
    public User create(User user) {
        User saved = users.save(user);
        settings.save(new UserSettings(saved));
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(long userId) {
        return users.findById(userId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByPublicId(UUID publicId) {
        return users.findByPublicId(publicId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return users.findByUsernameIgnoreCase(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return users.findByEmailIgnoreCase(email);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByIdentifier(String identifier) {
        return users.findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier);
    }

    @Transactional(readOnly = true)
    public boolean existsById(long userId) {
        return users.existsById(userId);
    }

    @Transactional
    public void recordGameResult(long userId, GameResult result) {
        int wins = result == GameResult.WIN ? 1 : 0;
        int losses = result == GameResult.LOSS ? 1 : 0;
        int draws = result == GameResult.DRAW ? 1 : 0;
        if (users.recordGameResult(userId, wins, losses, draws) != 1) {
            throw new IllegalStateException("Could not update player game statistics");
        }
    }
}
