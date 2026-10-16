package com.chess.backend.features.challenge.service;

import com.chess.backend.common.exception.UserNotFoundException;
import com.chess.backend.features.challenge.exception.ChallengeAlreadyPendingException;
import com.chess.backend.features.challenge.exception.ChallengeNotFoundException;
import com.chess.backend.features.challenge.exception.ChallengeRequiresFriendException;
import com.chess.backend.features.challenge.exception.ChallengeUnavailableException;
import com.chess.backend.features.challenge.exception.GameRoomNotFoundException;
import com.chess.backend.features.challenge.exception.InvalidTimeControlException;
import com.chess.backend.features.challenge.exception.SelfChallengeException;
import com.chess.backend.features.challenge.util.RoomCodeGenerator;


import com.chess.backend.features.challenge.dto.ChallengeEvent;
import com.chess.backend.features.challenge.dto.SendChallengeRequest;
import com.chess.backend.common.config.AppProperties;
import com.chess.backend.features.challenge.domain.GameChallenge;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.challenge.domain.enums.ChallengeStatus;
import com.chess.backend.features.challenge.domain.enums.GameSide;
import com.chess.backend.features.challenge.repository.GameChallengeRepository;
import com.chess.backend.features.user.service.UserDirectory;
import com.chess.backend.features.auth.security.JwtService;
import com.chess.backend.features.social.service.FriendService;
import com.chess.backend.features.social.service.PresenceService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeService {
    private final GameChallengeRepository challenges;
    private final UserDirectory users;
    private final FriendService friends;
    private final PresenceService presence;
    private final JwtService jwtService;
    private final SimpMessagingTemplate messaging;
    private final AppProperties properties;
    private final Clock clock;
    private final RoomCodeGenerator roomCodeGenerator;

    public ChallengeService(GameChallengeRepository challenges, UserDirectory users, FriendService friends,
                           PresenceService presence,
                           JwtService jwtService, SimpMessagingTemplate messaging, AppProperties properties, Clock clock,
                           RoomCodeGenerator roomCodeGenerator) {
        this.challenges = challenges; this.users = users; this.friends = friends; this.presence = presence;
        this.jwtService = jwtService;
        this.messaging = messaging; this.properties = properties; this.clock = clock;
        this.roomCodeGenerator = roomCodeGenerator;
    }

    @Transactional
    public void send(long challengerId, SendChallengeRequest request) {
        User challenger = requireUser(challengerId);
        User challenged = users.findByUsername(request.toUsername()).filter(User::isEmailVerified)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (challengerId == challenged.getId()) throw new SelfChallengeException("You cannot challenge yourself");
        if (!friends.areFriendsAndNotBlocked(challengerId, challenged.getId())) throw new ChallengeRequiresFriendException("Challenges are limited to friends");
        if ((request.initialTimeSeconds() == null) != (request.incrementSeconds() == null)) throw new InvalidTimeControlException("Both clock fields are required for a timed game");
        if (request.initialTimeSeconds() != null && (request.initialTimeSeconds() > 2592000 || request.incrementSeconds() > 3600))
            throw new InvalidTimeControlException("Time control is outside supported bounds");
        Instant now = clock.instant();
        if (challenges.hasPendingBetween(challengerId, challenged.getId(), now, ChallengeStatus.PENDING))
            throw new ChallengeAlreadyPendingException("A challenge is already pending");
        GameSide side = request.challengerSide() == null ? GameSide.RANDOM : request.challengerSide();
        GameChallenge challenge = challenges.save(new GameChallenge(challenger, challenged, request.initialTimeSeconds(),
                request.incrementSeconds(), side, now.plus(properties.invitationTtl())));
        messaging.convertAndSendToUser(challenged.getId().toString(), "/queue/challenges",
                new ChallengeEvent(challenge.getId(), ChallengeStatus.PENDING, challengerId, challenger.getUsername(), challenger.getDisplayName(),
                        request.initialTimeSeconds(), request.incrementSeconds(), side, null, null, null, null, null));
    }

    @Transactional
    public void accept(long userId, long challengeId) {
        GameChallenge challenge = challenges.lockById(challengeId)
                .filter(c -> c.getChallenged().getId() == userId && c.getStatus() == ChallengeStatus.PENDING)
                .orElseThrow(() -> new ChallengeNotFoundException("Pending challenge not found"));
        if (challenge.getExpiresAt().isBefore(clock.instant())) {
            challenge.setStatus(ChallengeStatus.EXPIRED);
            notifyStatus(challenge, userId, ChallengeStatus.EXPIRED);
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
        GameSide challengedColor = challengerColor == GameSide.WHITE ? GameSide.BLACK : GameSide.WHITE;
        challenge.accept(roomCode, challengerColor);
        presence.gameStarted(challenge.getChallenger().getId(), challenge.getChallenged().getId());
        sendMatch(challenge, challenge.getChallenger(), roomCode, challengerColor);
        sendMatch(challenge, challenge.getChallenged(), roomCode, challengedColor);
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
        challenges.expirePending(clock.instant(), ChallengeStatus.PENDING, ChallengeStatus.EXPIRED);
    }

    private void changePending(long id, long actor, boolean challengerOnly, ChallengeStatus status) {
        GameChallenge challenge = challenges.lockById(id).orElseThrow(() -> new ChallengeNotFoundException("Pending challenge not found"));
        boolean authorized = challengerOnly ? challenge.getChallenger().getId() == actor : challenge.getChallenged().getId() == actor;
        if (!authorized || challenge.getStatus() != ChallengeStatus.PENDING) throw new ChallengeNotFoundException("Pending challenge not found");
        challenge.setStatus(status);
        long recipient = challengerOnly ? challenge.getChallenged().getId() : challenge.getChallenger().getId();
        messaging.convertAndSendToUser(Long.toString(recipient), "/queue/challenges",
                new ChallengeEvent(challenge.getId(), status, actor, null, null, null, null, null, null, null, null, null, null));
    }

    private void notifyStatus(GameChallenge challenge, long actorId, ChallengeStatus status) {
        ChallengeEvent event = new ChallengeEvent(challenge.getId(), status, actorId,
                null, null, null, null, null, null, null, null, null, "Challenge has expired");
        messaging.convertAndSendToUser(challenge.getChallenger().getId().toString(), "/queue/challenges", event);
        messaging.convertAndSendToUser(challenge.getChallenged().getId().toString(), "/queue/challenges", event);
    }
    private void sendMatch(GameChallenge challenge, User recipient, String roomCode, GameSide color) {
        String token = jwtService.createRoomToken(roomCode, recipient.getId(), recipient.getDisplayName(), color,
                challenge.getInitialTimeSeconds(), challenge.getIncrementSeconds());
        String url = properties.engineWsUrl() + "?room=" + roomCode + "&token=" + token;
        messaging.convertAndSendToUser(recipient.getId().toString(), "/queue/challenges", new ChallengeEvent(challenge.getId(),
                ChallengeStatus.ACCEPTED, challenge.getChallenger().getId(), null, null, challenge.getInitialTimeSeconds(),
                challenge.getIncrementSeconds(), challenge.getChallengerSide(), roomCode, color, url, token, null));
    }
    private User requireUser(long id) { return users.findById(id).filter(User::isEmailVerified).orElseThrow(() -> new UserNotFoundException("User not found")); }
}
