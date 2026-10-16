package com.chess.backend.features.user.service;

import com.chess.backend.common.exception.NotFoundException;
import com.chess.backend.features.user.exception.AvatarReadException;
import com.chess.backend.features.user.exception.AvatarUploadEmptyException;


import com.chess.backend.common.exception.InvalidPaginationException;
import com.chess.backend.common.exception.UserNotFoundException;
import com.chess.backend.features.user.exception.AvatarContentMismatchException;
import com.chess.backend.features.user.exception.AvatarNotFoundException;
import com.chess.backend.features.user.exception.AvatarUploadTooLargeException;
import com.chess.backend.features.user.exception.ProfileNotFoundException;
import com.chess.backend.features.user.exception.SearchQueryTooShortException;
import com.chess.backend.features.user.exception.UnsupportedAvatarMediaTypeException;
import com.chess.backend.features.user.exception.UsernameAlreadyTakenException;


import com.chess.backend.common.config.AppProperties;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.user.dto.ProfileDtos.Page;
import com.chess.backend.features.user.dto.ProfileDtos.Profile;
import com.chess.backend.features.user.dto.ProfileDtos.SearchResult;
import com.chess.backend.features.user.dto.ProfileDtos.UpdateRequest;
import com.chess.backend.features.user.repository.UserRepository;
import com.chess.backend.features.user.storage.FileStorage;
import com.chess.backend.features.user.storage.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileService {
    private static final List<String> AVATAR_TYPES = List.of("image/png", "image/jpeg", "image/webp");
    private final UserRepository users;
    private final FileStorage storage;
    private final AppProperties properties;

    public ProfileService(UserRepository users, FileStorage storage, AppProperties properties) {
        this.users = users;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Profile me(long userId) { return toProfile(requireUser(userId)); }

    @Transactional(readOnly = true)
    public Profile publicProfile(String username) {
        return users.findByUsernameIgnoreCase(username).filter(User::isEmailVerified)
                .map(this::toProfile).orElseThrow(() -> new ProfileNotFoundException("Profile not found"));
    }

    @Transactional
    public Profile update(long userId, UpdateRequest request) {
        User user = requireUser(userId);
        if (request.username() != null) {
            String username = request.username().trim().toLowerCase(Locale.ROOT);
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
    public Page<SearchResult> search(long viewerId, String query, int page, int size) {
        if (query == null || query.trim().length() < 2) throw new SearchQueryTooShortException("Search query must contain at least two characters");
        if (page < 0 || size < 1 || size > 50) throw new InvalidPaginationException("Invalid page or page size");
        String needle = query.trim().toLowerCase(Locale.ROOT);
        var results = users.search(viewerId, needle + "%", "%" + needle + "%", PageRequest.of(page, size));
        List<SearchResult> items = results.getContent().stream().map(user -> new SearchResult(user.getId(), user.getUsername(),
                user.getDisplayName(), avatarUrl(user), user.getTotalGames(), user.getWins(), user.getLosses(), user.getDraws(), user.getId())).toList();
        return new Page<>(items, page, size, results.getTotalElements());
    }

    @Transactional
    public String uploadAvatar(long userId, MultipartFile file) {
        long maxSize = properties.avatarMaxSize().toBytes();
        if (file.isEmpty()) throw new AvatarUploadEmptyException("Avatar file is empty");
        if (file.getSize() > maxSize) throw new AvatarUploadTooLargeException("Avatar exceeds the maximum size");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!AVATAR_TYPES.contains(type)) throw new UnsupportedAvatarMediaTypeException("Avatar must be PNG, JPEG, or WebP");
        byte[] content;
        try { content = file.getBytes(); }
        catch (IOException exception) { throw new AvatarReadException("Could not read avatar upload"); }
        if (!matchesSignature(content, type)) throw new AvatarContentMismatchException("Avatar content does not match its image type");
        User user = requireUser(userId);
        String key = storage.store("avatars", new ByteArrayInputStream(content), content.length, type);
        String previous = user.getAvatarKey();
        user.setAvatarKey(key);
        if (previous != null) storage.delete(previous);
        return avatarUrl(user);
    }

    @Transactional(readOnly = true)
    public StoredFile avatar(long userId) {
        String key = requireUser(userId).getAvatarKey();
        if (key == null) throw new AvatarNotFoundException("Avatar not found");
        return storage.retrieve(key);
    }

    private User requireUser(long id) { return users.findById(id).orElseThrow(() -> new UserNotFoundException("User not found")); }
    private Profile toProfile(User user) { return new Profile(user.getId(), user.getUsername(), user.getDisplayName(), user.getBio(), avatarUrl(user),
            user.isSearchable(), user.getTotalGames(), user.getWins(), user.getLosses(), user.getDraws(), user.getId()); }
    private String avatarUrl(User user) { return user.getAvatarKey() == null ? null : "/api/users/" + user.getId() + "/avatar"; }
    private static boolean matchesSignature(byte[] bytes, String type) {
        if (type.equals("image/png")) return bytes.length >= 8 && (bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71;
        if (type.equals("image/jpeg")) return bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
        return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }
}
