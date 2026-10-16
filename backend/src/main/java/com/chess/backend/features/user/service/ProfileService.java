package com.chess.backend.features.user.service;

import com.chess.backend.common.exception.InvalidPaginationException;
import com.chess.backend.common.exception.UserNotFoundException;
import com.chess.backend.features.user.exception.AvatarNotFoundException;
import com.chess.backend.features.user.exception.ProfileNotFoundException;
import com.chess.backend.features.user.exception.SearchQueryTooShortException;
import com.chess.backend.features.user.exception.UsernameAlreadyTakenException;


import com.chess.backend.common.config.AppProperties;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.user.dto.ProfilePage;
import com.chess.backend.features.user.dto.Profile;
import com.chess.backend.features.user.dto.SearchResult;
import com.chess.backend.features.user.dto.ProfileUpdateRequest;
import com.chess.backend.features.user.repository.UserRepository;
import com.chess.backend.features.user.storage.FileStorage;
import com.chess.backend.features.user.storage.StoredFile;
import com.chess.backend.features.user.util.AvatarContentValidator;
import com.chess.backend.features.user.util.ValidatedAvatar;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileService {
    private final UserRepository users;
    private final FileStorage storage;
    private final AvatarStorageService avatarStorage;
    private final AppProperties properties;
    private final AvatarContentValidator avatarValidator;

    public ProfileService(UserRepository users, FileStorage storage, AvatarStorageService avatarStorage,
                          AppProperties properties, AvatarContentValidator avatarValidator) {
        this.users = users;
        this.storage = storage;
        this.avatarStorage = avatarStorage;
        this.properties = properties;
        this.avatarValidator = avatarValidator;
    }

    @Transactional(readOnly = true)
    public Profile me(long userId) { return toProfile(requireUser(userId)); }

    @Transactional(readOnly = true)
    public Profile publicProfile(String username) {
        return users.findByUsernameIgnoreCase(username).filter(User::isEmailVerified)
                .map(this::toProfile).orElseThrow(() -> new ProfileNotFoundException("Profile not found"));
    }

    @Transactional
    public Profile update(long userId, ProfileUpdateRequest request) {
        User user = requireUser(userId);
        if (request.username() != null) {
            String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
            users.findByUsernameIgnoreCase(username).filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(existing -> { throw new UsernameAlreadyTakenException("Username is already taken"); });
            user.setUsername(username);
        }
        if (request.displayName() != null) user.setDisplayName(request.displayName().trim());
        user.setBio(request.bio());
        if (request.searchable() != null) user.setSearchable(request.searchable());
        return toProfile(user);
    }

    @Transactional(readOnly = true)
    public ProfilePage<SearchResult> search(long viewerId, String query, int page, int size) {
        if (query == null || query.trim().length() < 2) throw new SearchQueryTooShortException("Search query must contain at least two characters");
        if (page < 0 || size < 1 || size > 50) throw new InvalidPaginationException("Invalid page or page size");
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        var results = users.search(viewerId, needle + "%", "%" + needle + "%", PageRequest.of(page, size));
        List<SearchResult> items = results.getContent().stream().map(user -> new SearchResult(user.getId(), user.getPublicId(), user.getUsername(),
                user.getDisplayName(), avatarUrl(user), user.getTotalGames(), user.getWins(), user.getLosses(), user.getDraws(), user.getId())).toList();
        return new ProfilePage<>(items, page, size, results.getTotalElements());
    }

    @Transactional
    public String uploadAvatar(long userId, MultipartFile file) {
        ValidatedAvatar avatar = avatarValidator.validate(file, properties.avatarMaxSize().toBytes());
        User user = requireUser(userId);
        byte[] content = avatar.content();
        String previous = user.getAvatarKey();
        String key = avatarStorage.replace(content, avatar.mediaType(), previous);
        user.setAvatarKey(key);
        return avatarUrl(user);
    }

    @Transactional(readOnly = true)
    public StoredFile avatar(UUID publicId) {
        String key = users.findByPublicId(publicId).orElseThrow(() -> new UserNotFoundException("User not found")).getAvatarKey();
        if (key == null) throw new AvatarNotFoundException("Avatar not found");
        return storage.retrieve(key);
    }

    private User requireUser(long id) { return users.findById(id).orElseThrow(() -> new UserNotFoundException("User not found")); }
    private Profile toProfile(User user) { return new Profile(user.getId(), user.getPublicId(), user.getUsername(), user.getDisplayName(), user.getBio(), avatarUrl(user),
            user.isSearchable(), user.getTotalGames(), user.getWins(), user.getLosses(), user.getDraws(), user.getId()); }
    private String avatarUrl(User user) { return user.getAvatarKey() == null ? null : "/api/users/" + user.getPublicId() + "/avatar"; }
}
