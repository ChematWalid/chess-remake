package com.chess.backend.social;

import com.chess.backend.config.ApiException;
import com.chess.backend.domain.entity.FriendInvite;
import com.chess.backend.domain.entity.Friendship;
import com.chess.backend.domain.entity.User;
import com.chess.backend.repository.FriendInviteRepository;
import com.chess.backend.repository.FriendshipRepository;
import com.chess.backend.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final FriendshipRepository friendships;
    private final FriendInviteRepository invites;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    public FriendService(UserRepository users, FriendshipRepository friendships, FriendInviteRepository invites,
                         SimpMessagingTemplate messaging, Clock clock) {
        this.users = users; this.friendships = friendships; this.invites = invites; this.messaging = messaging; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FriendView> friends(long userId) {
        return friendships.findForUser(userId, "ACCEPTED").stream().map(f -> {
            User other = f.getRequester().getId() == userId ? f.getAddressee() : f.getRequester();
            return new FriendView(other.getId(), other.getUsername(), other.getDisplayName(), "OFFLINE");
        }).toList();
    }
    @Transactional(readOnly = true) public List<RequestView> incoming(long userId) { return mapRequests(friendships.incoming(userId), true); }
    @Transactional(readOnly = true) public List<RequestView> outgoing(long userId) { return mapRequests(friendships.outgoing(userId), false); }

    @Transactional
    public void request(long requesterId, String targetUsername) {
        User requester = requireUser(requesterId);
        User target = users.findByUsernameIgnoreCase(targetUsername).filter(User::isEmailVerified)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        ensureDifferent(requesterId, target.getId());
        Friendship relation = friendships.lockPair(requesterId, target.getId()).orElse(null);
        if (relation != null) {
            if ("BLOCKED".equals(relation.getStatus())) throw unavailable();
            if ("ACCEPTED".equals(relation.getStatus())) throw new ApiException(HttpStatus.CONFLICT, "You are already friends");
            if ("PENDING".equals(relation.getStatus())) {
                if (relation.getRequester().getId().equals(target.getId())) { relation.setStatus("ACCEPTED"); return; }
                throw new ApiException(HttpStatus.CONFLICT, "A friend request is already pending");
            }
            relation.setStatus("PENDING");
        } else friendships.save(new Friendship(requester, target, "PENDING"));
        messaging.convertAndSendToUser(target.getId().toString(), "/queue/notifications", new Notification("FRIEND_REQUEST", requester.getId().toString()));
    }

    @Transactional public void accept(long userId, long requestId) { changeRequest(userId, requestId, "ACCEPTED"); }
    @Transactional public void decline(long userId, long requestId) { changeRequest(userId, requestId, "DECLINED"); }

    @Transactional
    public void remove(long userId, long otherUserId) {
        Friendship relation = friendships.lockPair(userId, otherUserId).filter(f -> "ACCEPTED".equals(f.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Friendship not found"));
        friendships.delete(relation);
    }

    @Transactional
    public void block(long blockerId, long targetId) {
        ensureDifferent(blockerId, targetId);
        User blocker = requireUser(blockerId);
        User target = requireUser(targetId);
        Friendship relation = friendships.lockPair(blockerId, targetId).orElse(null);
        if (relation == null) friendships.save(new Friendship(blocker, target, "BLOCKED"));
        else if (!"BLOCKED".equals(relation.getStatus()) || !relation.getRequester().getId().equals(blockerId)) {
            friendships.delete(relation);
            friendships.flush();
            friendships.save(new Friendship(blocker, target, "BLOCKED"));
        }
    }

    @Transactional
    public void unblock(long blockerId, long targetId) {
        Friendship relation = friendships.lockPair(blockerId, targetId).filter(f -> "BLOCKED".equals(f.getStatus())
                && f.getRequester().getId().equals(blockerId)).orElse(null);
        if (relation != null) friendships.delete(relation);
    }

    @Transactional
    public String createInvite(long ownerId) {
        User owner = requireUser(ownerId);
        invites.deleteByOwner_IdAndConsumedAtIsNull(ownerId);
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        invites.save(new FriendInvite(owner, hash(token)));
        return token;
    }

    @Transactional
    public void redeemInvite(long visitorId, String token) {
        User visitor = requireUser(visitorId);
        FriendInvite invite = invites.findActiveByHash(hash(token)).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Friend link is invalid or has already been used"));
        User owner = invite.getOwner();
        ensureDifferent(visitorId, owner.getId());
        Friendship relation = friendships.lockPair(visitorId, owner.getId()).orElse(null);
        if (relation != null && "BLOCKED".equals(relation.getStatus())) throw unavailable();
        invite.consume(visitor, Instant.now(clock));
        if (relation == null) friendships.save(new Friendship(owner, visitor, "ACCEPTED"));
        else relation.setStatus("ACCEPTED");
    }

    @Transactional(readOnly = true)
    public List<Long> friendIds(long userId) {
        return friendships.findForUser(userId, "ACCEPTED").stream().map(f -> f.getRequester().getId() == userId
                ? f.getAddressee().getId() : f.getRequester().getId()).toList();
    }
    @Transactional(readOnly = true) public boolean areFriendsAndNotBlocked(long firstId, long secondId) { return friendships.areFriends(firstId, secondId); }

    private void changeRequest(long userId, long requestId, String status) {
        Friendship friendship = friendships.findById(requestId).filter(f -> f.getAddressee().getId() == userId && "PENDING".equals(f.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Incoming request not found"));
        friendship.setStatus(status);
    }
    private List<RequestView> mapRequests(List<Friendship> rows, boolean incoming) {
        return rows.stream().map(f -> {
            User other = incoming ? f.getRequester() : f.getAddressee();
            return new RequestView(f.getId(), other.getId(), other.getUsername(), other.getDisplayName(), f.getCreatedAt());
        }).toList();
    }
    private User requireUser(long id) { return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found")); }
    private static void ensureDifferent(long first, long second) { if (first == second) throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot perform this action with yourself"); }
    private static ApiException unavailable() { return new ApiException(HttpStatus.FORBIDDEN, "This action is unavailable"); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    public record FriendView(long id, String username, String displayName, String presence) { }
    public record RequestView(long requestId, long userId, String username, String displayName, Instant createdAt) { }
    public record Notification(String type, String subjectId) { }
}
