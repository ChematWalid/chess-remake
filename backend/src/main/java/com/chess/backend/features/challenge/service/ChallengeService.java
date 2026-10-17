package com.chess.backend.features.challenge.service;

import com.chess.backend.common.config.AppProperties;
import com.chess.backend.common.exception.InvalidPaginationException;
import com.chess.backend.common.exception.UserNotFoundException;
import com.chess.backend.common.service.UserMessagePublisher;
import com.chess.backend.features.auth.security.JwtService;
import com.chess.backend.features.challenge.domain.GameChallenge;
import com.chess.backend.features.challenge.domain.enums.ChallengeEventType;
import com.chess.backend.features.challenge.domain.enums.ChallengeStatus;
import com.chess.backend.features.challenge.domain.enums.GameSide;
import com.chess.backend.features.challenge.dto.ChallengeDetail;
import com.chess.backend.features.challenge.dto.ChallengeEvent;
import com.chess.backend.features.challenge.dto.ChallengePage;
import com.chess.backend.features.challenge.dto.ChallengeSummary;
import com.chess.backend.features.challenge.dto.SendChallengeRequest;
import com.chess.backend.features.challenge.exception.ChallengeAlreadyPendingException;
import com.chess.backend.features.challenge.exception.ChallengeNotFoundException;
import com.chess.backend.features.challenge.exception.ChallengeRequiresFriendException;
import com.chess.backend.features.challenge.exception.ChallengeUnavailableException;
import com.chess.backend.features.challenge.exception.GameRoomNotFoundException;
import com.chess.backend.features.challenge.exception.InvalidTimeControlException;
import com.chess.backend.features.challenge.exception.SelfChallengeException;
import com.chess.backend.features.challenge.repository.GameChallengeRepository;
import com.chess.backend.features.challenge.util.RoomCodeGenerator;
import com.chess.backend.features.position.validation.PositionValidator;
import com.chess.backend.features.challenge.exception.InvalidChallengePositionException;
import com.chess.backend.features.social.service.FriendService;
import com.chess.backend.features.social.service.PresenceService;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.user.service.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeService {
    private static final int EXPIRATION_BATCH_SIZE = 100;
    private final GameChallengeRepository challenges;
    private final UserDirectory users;
    private final FriendService friends;
    private final PresenceService presence;
    private final JwtService jwtService;
    private final UserMessagePublisher messages;
    private final AppProperties properties;
    private final Clock clock;
    private final RoomCodeGenerator roomCodeGenerator;

    public ChallengeService(GameChallengeRepository challenges, UserDirectory users, FriendService friends,
                            PresenceService presence, JwtService jwtService, UserMessagePublisher messages,
                            AppProperties properties, Clock clock, RoomCodeGenerator roomCodeGenerator) {
        this.challenges = challenges;
        this.users = users;
        this.friends = friends;
        this.presence = presence;
        this.jwtService = jwtService;
        this.messages = messages;
        this.properties = properties;
        this.clock = clock;
        this.roomCodeGenerator = roomCodeGenerator;
    }

    @Transactional
    public void send(long challengerId, SendChallengeRequest request) {
        User challenger = requireUser(challengerId);
        User challenged = users.findByUsername(request.toUsername()).filter(User::isEmailVerified)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (challengerId == challenged.getId()) throw new SelfChallengeException("You cannot challenge yourself");
        if (!friends.areFriendsAndNotBlocked(challengerId, challenged.getId()))
            throw new ChallengeRequiresFriendException("Challenges are limited to friends");
        if ((request.initialTimeSeconds() == null) != (request.incrementSeconds() == null))
            throw new InvalidTimeControlException("Both clock fields are required for a timed game");
        if (request.initialTimeSeconds() != null
                && (request.initialTimeSeconds() > 2592000 || request.incrementSeconds() > 3600))
            throw new InvalidTimeControlException("Time control is outside supported bounds");
        List<String> setupMoves = request.setupMoves() == null ? List.of() : request.setupMoves();
        List<String> setupFens = request.setupFens() == null ? List.of() : request.setupFens();
        String positionError = PositionValidator.validate(request.initialFen(), setupMoves, setupFens);
        if (positionError != null) throw new InvalidChallengePositionException(positionError);
        Instant now = clock.instant();
        if (challenges.hasPendingBetween(challengerId, challenged.getId(), now, ChallengeStatus.PENDING))
            throw new ChallengeAlreadyPendingException("A challenge is already pending");
        GameSide side = request.challengerSide() == null ? GameSide.RANDOM : request.challengerSide();
        GameChallenge challenge = challenges.save(new GameChallenge(challenger, challenged, request.initialTimeSeconds(),
                request.incrementSeconds(), side, request.initialFen(), setupMoves, setupFens, now.plus(properties.invitationTtl())));
        sendChallengeEvent(challenged.getId(), challenge, ChallengeEventType.INVITATION_RECEIVED,
                challenger.getPublicId(), challenger.getUsername(), challenger.getDisplayName(), null);
        sendChallengeEvent(challenger.getId(), challenge, ChallengeEventType.INVITATION_SENT,
                challenger.getPublicId(), challenged.getUsername(), challenged.getDisplayName(), null);
    }

    @Transactional
    public void accept(long userId, long challengeId) {
        GameChallenge challenge = challenges.lockById(challengeId)
                .filter(value -> value.getChallenged().getId() == userId && value.getStatus() == ChallengeStatus.PENDING)
                .orElseThrow(() -> new ChallengeNotFoundException("Pending challenge not found"));
        if (!challenge.getExpiresAt().isAfter(clock.instant())) {
            challenge.setStatus(ChallengeStatus.EXPIRED);
            notifyStatus(challenge, null, ChallengeStatus.EXPIRED);
            return;
        }
        if (!friends.areFriendsAndNotBlocked(challenge.getChallenger().getId(), challenge.getChallenged().getId()))
            throw new ChallengeUnavailableException("Challenge is unavailable");
        String roomCode = roomCodeGenerator.generate();
        GameSide challengerColor = switch (challenge.getChallengerSide()) {
            case WHITE -> GameSide.WHITE;
            case BLACK -> GameSide.BLACK;
            case RANDOM -> roomCodeGenerator.randomBoolean() ? GameSide.WHITE : GameSide.BLACK;
        };
        GameSide challengedColor = opposite(challengerColor);
        challenge.accept(roomCode, challengerColor, clock.instant());
        presence.gameStarted(challenge.getChallenger().getId(), challenge.getChallenged().getId());
        sendMatch(challenge, challenge.getChallenger(), challengerColor);
        sendMatch(challenge, challenge.getChallenged(), challengedColor);
    }

    @Transactional(readOnly = true)
    public ChallengePage incoming(long userId, int page, int size) {
        return toPage(challenges.findByChallenged_IdOrderByCreatedAtDesc(userId, pageable(page, size)), userId, page, size);
    }

    @Transactional(readOnly = true)
    public ChallengePage outgoing(long userId, int page, int size) {
        return toPage(challenges.findByChallenger_IdOrderByCreatedAtDesc(userId, pageable(page, size)), userId, page, size);
    }

    @Transactional(readOnly = true)
    public ChallengeDetail detail(long userId, long challengeId) {
        GameChallenge challenge = challenges.findById(challengeId)
                .orElseThrow(() -> new ChallengeNotFoundException("Challenge not found"));
        boolean challenger = challenge.getChallenger().getId() == userId;
        boolean challenged = challenge.getChallenged().getId() == userId;
        if (!challenger && !challenged) throw new ChallengeNotFoundException("Challenge not found");
        User caller = challenger ? challenge.getChallenger() : challenge.getChallenged();
        User opponent = challenger ? challenge.getChallenged() : challenge.getChallenger();
        GameSide color = playerColor(challenge, challenger);
        String token = null;
        String connectUrl = null;
        if (challenge.getStatus() == ChallengeStatus.ACCEPTED) {
            token = jwtService.createRoomToken(challenge.getRoomCode(), caller.getId(), caller.getDisplayName(), color,
                    challenge.getInitialTimeSeconds(), challenge.getIncrementSeconds(), challenge.getInitialFen(), challenge.getSetupMoves(), challenge.getSetupFens());
            connectUrl = properties.engineWsUrl();
        }
        return new ChallengeDetail(challenge.getId(), challenge.getStatus(), opponent.getPublicId(), opponent.getUsername(),
                challenge.getInitialTimeSeconds(), challenge.getIncrementSeconds(), challenge.getChallengerSide(), color,
                challenge.getRoomCode(), connectUrl, token, challenge.getExpiresAt(), challenge.getInitialFen(), challenge.getSetupMoves(), challenge.getSetupFens());
    }

    @Transactional
    public GameChallenge lockRoom(String roomCode) {
        return challenges.lockByRoomCode(roomCode)
                .orElseThrow(() -> new GameRoomNotFoundException("Game room not found"));
    }

    @Transactional
    public void complete(GameChallenge challenge) {
        challenge.setStatus(ChallengeStatus.COMPLETED);
        presence.gameCompleted(challenge.getChallenger().getId(), challenge.getChallenged().getId());
    }

    @Transactional
    public void expireNotStarted() {
        Instant deadline = clock.instant().minus(properties.gameStartTimeout());
        var expired = challenges.lockNotStartedBefore(deadline, ChallengeStatus.ACCEPTED,
                PageRequest.of(0, EXPIRATION_BATCH_SIZE));
        for (GameChallenge challenge : expired) {
            challenge.setStatus(ChallengeStatus.EXPIRED);
            presence.gameCompleted(challenge.getChallenger().getId(), challenge.getChallenged().getId());
            ChallengeEvent event = event(challenge, ChallengeEventType.STATUS_CHANGED, ChallengeStatus.EXPIRED,
                    null, null, null, null, null, null, null,
                    "Game room expired before both players connected");
            messages.publish(challenge.getChallenger().getId(), "/topic/challenges", event);
            messages.publish(challenge.getChallenged().getId(), "/topic/challenges", event);
        }
    }

    @Transactional
    public void decline(long userId, long challengeId) {
        changePending(challengeId, userId, false, ChallengeStatus.DECLINED);
    }

    @Transactional
    public void cancel(long userId, long challengeId) {
        changePending(challengeId, userId, true, ChallengeStatus.CANCELED);
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void expirePending() {
        var expired = challenges.lockExpired(clock.instant(), ChallengeStatus.PENDING, PageRequest.of(0, EXPIRATION_BATCH_SIZE));
        for (GameChallenge challenge : expired) {
            challenge.setStatus(ChallengeStatus.EXPIRED);
            notifyStatus(challenge, null, ChallengeStatus.EXPIRED);
        }
    }

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void expireAcceptedNotStarted() {
        expireNotStarted();
    }

    private void changePending(long challengeId, long actorId, boolean challengerOnly, ChallengeStatus status) {
        GameChallenge challenge = challenges.lockById(challengeId)
                .orElseThrow(() -> new ChallengeNotFoundException("Pending challenge not found"));
        boolean authorized = challengerOnly ? challenge.getChallenger().getId() == actorId
                : challenge.getChallenged().getId() == actorId;
        if (!authorized || challenge.getStatus() != ChallengeStatus.PENDING)
            throw new ChallengeNotFoundException("Pending challenge not found");
        challenge.setStatus(status);
        UUID actorPublicId = publicUserId(actorId);
        sendChallengeEvent(challenge.getChallenger().getId(), challenge, ChallengeEventType.STATUS_CHANGED,
                actorPublicId, null, null, null);
        sendChallengeEvent(challenge.getChallenged().getId(), challenge, ChallengeEventType.STATUS_CHANGED,
                actorPublicId, null, null, null);
    }

    private void notifyStatus(GameChallenge challenge, UUID actorPublicId, ChallengeStatus status) {
        ChallengeEvent event = event(challenge, ChallengeEventType.STATUS_CHANGED, status, actorPublicId,
                null, null, null, null, null, null, "Challenge has expired");
        messages.publish(challenge.getChallenger().getId(), "/topic/challenges", event);
        messages.publish(challenge.getChallenged().getId(), "/topic/challenges", event);
    }

    private void sendMatch(GameChallenge challenge, User recipient, GameSide color) {
        String url = properties.engineWsUrl();
        messages.publish(recipient.getId(), "/topic/challenges",
                event(challenge, ChallengeEventType.MATCH_ASSIGNED, ChallengeStatus.ACCEPTED,
                        challenge.getChallenger().getPublicId(), null, null, color, challenge.getRoomCode(), url, null, null));
    }

    private void sendChallengeEvent(long recipientId, GameChallenge challenge, ChallengeEventType type,
                                    UUID actorPublicId, String username, String displayName, String message) {
        ChallengeEvent event = event(challenge, type, challenge.getStatus(), actorPublicId, username, displayName,
                null, null, null, null, message);
        messages.publish(recipientId, "/topic/challenges", event);
    }

    private ChallengeEvent event(GameChallenge challenge, ChallengeEventType type, ChallengeStatus status,
                                 UUID actorPublicId, String username, String displayName, GameSide color,
                                 String roomCode, String connectUrl, String token, String message) {
        return new ChallengeEvent(challenge.getId(), type, status, actorPublicId, username, displayName,
                challenge.getInitialTimeSeconds(), challenge.getIncrementSeconds(), challenge.getChallengerSide(),
                roomCode, color, connectUrl, token, message, challenge.getInitialFen(), challenge.getSetupMoves(), challenge.getSetupFens());
    }

    private UUID publicUserId(long userId) {
        return users.findById(userId).map(User::getPublicId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private User requireUser(long userId) {
        return users.findById(userId).filter(User::isEmailVerified)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private static PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new InvalidPaginationException("Invalid page or page size");
        return PageRequest.of(page, size);
    }

    private static ChallengePage toPage(Page<GameChallenge> result, long userId, int page, int size) {
        var items = result.getContent().stream().map(challenge -> {
            boolean challenger = challenge.getChallenger().getId() == userId;
            return new ChallengeSummary(challenge.getId(), challenge.getStatus(), challenge.getChallenger().getPublicId(),
                    challenge.getChallenger().getUsername(), challenge.getChallenged().getPublicId(),
                    challenge.getChallenged().getUsername(), challenge.getInitialTimeSeconds(), challenge.getIncrementSeconds(),
                    challenge.getChallengerSide(), playerColor(challenge, challenger), challenge.getRoomCode(),
                    challenge.getCreatedAt(), challenge.getExpiresAt(), challenge.getInitialFen(), challenge.getSetupMoves(), challenge.getSetupFens());
        }).toList();
        return new ChallengePage(items, page, size, result.getTotalElements());
    }

    private static GameSide playerColor(GameChallenge challenge, boolean challenger) {
        if (challenge.getChallengerColor() == null) return null;
        return challenger ? challenge.getChallengerColor() : opposite(challenge.getChallengerColor());
    }

    private static GameSide opposite(GameSide color) {
        return color == GameSide.WHITE ? GameSide.BLACK : GameSide.WHITE;
    }
}
