package com.chess.backend.features.social.service;

import com.chess.backend.features.social.exception.FriendRequestNotFoundException;
import com.chess.backend.features.social.dto.FriendSummary;
import com.chess.backend.features.social.dto.FriendRequestSummary;
import com.chess.backend.features.social.dto.UserNotification;
import com.chess.backend.features.social.domain.enums.NotificationType;


import com.chess.backend.common.exception.UserNotFoundException;
import com.chess.backend.common.service.UserMessagePublisher;
import com.chess.backend.features.social.exception.AlreadyFriendsException;
import com.chess.backend.features.social.exception.FriendInviteInvalidException;
import com.chess.backend.features.social.exception.FriendRequestAlreadyPendingException;
import com.chess.backend.features.social.exception.FriendshipNotFoundException;
import com.chess.backend.features.social.exception.SelfFriendActionException;

import com.chess.backend.features.social.exception.FriendshipUnavailableException;


import com.chess.backend.features.social.domain.FriendInvite;
import com.chess.backend.features.social.domain.Friendship;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.social.domain.enums.FriendshipStatus;
import com.chess.backend.features.social.repository.FriendInviteRepository;
import com.chess.backend.features.social.repository.FriendshipRepository;
import com.chess.backend.features.user.service.UserDirectory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserDirectory users;
    private final FriendshipRepository friendships;
    private final FriendInviteRepository invites;
    private final UserMessagePublisher messages;
    private final Clock clock;

    public FriendService(UserDirectory users, FriendshipRepository friendships, FriendInviteRepository invites,
                         UserMessagePublisher messages, Clock clock) {
        this.users = users; this.friendships = friendships; this.invites = invites; this.messages = messages; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FriendSummary> friends(long userId) {
        return friendships.findForUser(userId, FriendshipStatus.ACCEPTED).stream().map(f -> {
            User other = f.getRequester().getId() == userId ? f.getAddressee() : f.getRequester();
            return new FriendSummary(other.getPublicId(), other.getId(), other.getUsername(), other.getDisplayName());
        }).toList();
    }
    @Transactional(readOnly = true)
    public List<FriendSummary> blocked(long userId) {
        return friendships.findForUser(userId, FriendshipStatus.BLOCKED).stream()
                .filter(f -> f.getRequester().getId() == userId)
                .map(f -> new FriendSummary(f.getAddressee().getPublicId(), f.getAddressee().getId(),
                        f.getAddressee().getUsername(), f.getAddressee().getDisplayName()))
                .toList();
    }
    @Transactional(readOnly = true)
    public List<FriendRequestSummary> incoming(long userId) {
        return mapRequests(friendships.incoming(userId, FriendshipStatus.PENDING), true);
    }

    @Transactional(readOnly = true)
    public List<FriendRequestSummary> outgoing(long userId) {
        return mapRequests(friendships.outgoingHistory(userId, List.of(FriendshipStatus.PENDING, FriendshipStatus.DECLINED)), false);
    }

    @Transactional
    public void request(long requesterId, String targetUsername) {
        User requester = requireUser(requesterId);
        User target = users.findByUsername(targetUsername).filter(User::isEmailVerified)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        ensureDifferent(requesterId, target.getId());
        Friendship relation = friendships.lockPair(requesterId, target.getId()).orElse(null);
        if (relation != null) {
            if (relation.getStatus() == FriendshipStatus.BLOCKED) throw unavailable();
            if (relation.getStatus() == FriendshipStatus.ACCEPTED) throw new AlreadyFriendsException("You are already friends");
            if (relation.getStatus() == FriendshipStatus.PENDING) {
                if (relation.getRequester().getId().equals(target.getId())) {
                    relation.setStatus(FriendshipStatus.ACCEPTED);
                    notifyStatus(relation.getRequester(), NotificationType.FRIEND_REQUEST_ACCEPTED, requester.getPublicId());
                    notifyStatus(relation.getAddressee(), NotificationType.FRIEND_REQUEST_ACCEPTED, target.getPublicId());
                    return;
                }
                throw new FriendRequestAlreadyPendingException("A friend request is already pending");
            }
            if (relation.getStatus() == FriendshipStatus.DECLINED
                    && !relation.getRequester().getId().equals(requesterId)) {
                friendships.delete(relation);
                friendships.flush();
                friendships.save(new Friendship(requester, target, FriendshipStatus.PENDING));
            } else {
                relation.setStatus(FriendshipStatus.PENDING);
            }
        } else friendships.save(new Friendship(requester, target, FriendshipStatus.PENDING));
        messages.publish(target.getId(), "/queue/notifications",
                new UserNotification(NotificationType.FRIEND_REQUEST, requester.getPublicId().toString()));
    }

    @Transactional
    public void accept(long userId, long requestId) {
        changeRequest(userId, requestId, FriendshipStatus.ACCEPTED);
    }

    @Transactional
    public void decline(long userId, long requestId) {
        changeRequest(userId, requestId, FriendshipStatus.DECLINED);
    }

    @Transactional
    public void remove(long userId, long otherUserId) {
        Friendship relation = friendships.lockPair(userId, otherUserId).filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new FriendshipNotFoundException("Friendship not found"));
        friendships.delete(relation);
    }

    @Transactional
    public void remove(long userId, UUID otherPublicId) {
        User other = users.findByPublicId(otherPublicId).orElseThrow(() -> new UserNotFoundException("User not found"));
        remove(userId, other.getId());
    }

    @Transactional
    public void block(long blockerId, long targetId) {
        ensureDifferent(blockerId, targetId);
        User blocker = requireUser(blockerId);
        User target = requireUser(targetId);
        Friendship relation = friendships.lockPair(blockerId, targetId).orElse(null);
        if (relation == null) friendships.save(new Friendship(blocker, target, FriendshipStatus.BLOCKED));
        else if (relation.getStatus() != FriendshipStatus.BLOCKED || !relation.getRequester().getId().equals(blockerId)) {
            friendships.delete(relation);
            friendships.flush();
            friendships.save(new Friendship(blocker, target, FriendshipStatus.BLOCKED));
        }
    }

    @Transactional
    public void block(long blockerId, UUID targetPublicId) {
        User target = users.findByPublicId(targetPublicId).orElseThrow(() -> new UserNotFoundException("User not found"));
        block(blockerId, target.getId());
    }

    @Transactional
    public void unblock(long blockerId, long targetId) {
        Friendship relation = friendships.lockPair(blockerId, targetId).filter(f -> f.getStatus() == FriendshipStatus.BLOCKED
                && f.getRequester().getId().equals(blockerId)).orElse(null);
        if (relation != null) friendships.delete(relation);
    }

    @Transactional
    public void unblock(long blockerId, UUID targetPublicId) {
        User target = users.findByPublicId(targetPublicId).orElseThrow(() -> new UserNotFoundException("User not found"));
        unblock(blockerId, target.getId());
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
        FriendInvite invite = invites.findActiveByHash(hash(token)).orElseThrow(() -> new FriendInviteInvalidException("Friend link is invalid or has already been used"));
        User owner = invite.getOwner();
        ensureDifferent(visitorId, owner.getId());
        Friendship relation = friendships.lockPair(visitorId, owner.getId()).orElse(null);
        if (relation != null && relation.getStatus() == FriendshipStatus.BLOCKED) throw unavailable();
        invite.consume(visitor, clock.instant());
        if (relation == null) friendships.save(new Friendship(owner, visitor, FriendshipStatus.ACCEPTED));
        else relation.setStatus(FriendshipStatus.ACCEPTED);
    }

    @Transactional(readOnly = true)
    public List<Long> friendIds(long userId) {
        return friendships.findForUser(userId, FriendshipStatus.ACCEPTED).stream().map(f -> f.getRequester().getId() == userId
                ? f.getAddressee().getId() : f.getRequester().getId()).toList();
    }
    @Transactional(readOnly = true)
    public boolean areFriendsAndNotBlocked(long firstId, long secondId) {
        return friendships.areFriends(firstId, secondId, FriendshipStatus.ACCEPTED);
    }

    private void changeRequest(long userId, long requestId, FriendshipStatus status) {
        Friendship friendship = friendships.findById(requestId).filter(f -> f.getAddressee().getId() == userId && f.getStatus() == FriendshipStatus.PENDING)
                .orElseThrow(() -> new FriendRequestNotFoundException("Incoming request not found"));
        friendship.setStatus(status);
        NotificationType notificationType = status == FriendshipStatus.ACCEPTED
                ? NotificationType.FRIEND_REQUEST_ACCEPTED : NotificationType.FRIEND_REQUEST_DECLINED;
        notifyStatus(friendship.getRequester(), notificationType, friendship.getAddressee().getPublicId());
    }
    private List<FriendRequestSummary> mapRequests(List<Friendship> rows, boolean incoming) {
        return rows.stream().map(f -> {
            User other = incoming ? f.getRequester() : f.getAddressee();
            return new FriendRequestSummary(f.getId(), other.getPublicId(), other.getId(), other.getUsername(),
                    other.getDisplayName(), f.getStatus(), f.getCreatedAt());
        }).toList();
    }
    private User requireUser(long id) { return users.findById(id).orElseThrow(() -> new UserNotFoundException("User not found")); }
    private static void ensureDifferent(long first, long second) { if (first == second) throw new SelfFriendActionException("You cannot perform this action with yourself"); }
    private static FriendshipUnavailableException unavailable() { return new FriendshipUnavailableException( "This action is unavailable"); }
    private void notifyStatus(User recipient, NotificationType type, UUID subjectId) {
        messages.publish(recipient.getId(), "/queue/notifications", new UserNotification(type, subjectId.toString()));
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    }
