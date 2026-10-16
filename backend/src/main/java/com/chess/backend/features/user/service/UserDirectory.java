package com.chess.backend.features.user.service;

import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDirectory {
    private final UserRepository users;

    public UserDirectory(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public User create(User user) {
        return users.save(user);
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
}
