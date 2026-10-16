package com.chess.backend.features.challenge.service;

import com.chess.backend.common.exception.NotFoundException;


import com.chess.backend.common.exception.UserNotFoundException;
import com.chess.backend.features.challenge.exception.ChallengeAlreadyPendingException;
import com.chess.backend.features.challenge.exception.ChallengeNotFoundException;
import com.chess.backend.features.challenge.exception.ChallengeRequiresFriendException;
import com.chess.backend.features.challenge.exception.ChallengeUnavailableException;
import com.chess.backend.features.challenge.exception.GameRoomInactiveException;
import com.chess.backend.features.challenge.exception.GameRoomNotFoundException;
import com.chess.backend.features.challenge.exception.InvalidTimeControlException;
import com.chess.backend.features.challenge.exception.SelfChallengeException;


import com.chess.backend.features.challenge.dto.ChallengeDtos.ChallengeEvent;
import com.chess.backend.features.challenge.dto.ChallengeDtos.SendRequest;
import com.chess.backend.common.config.AppProperties;
import com.chess.backend.features.challenge.domain.GameChallenge;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.challenge.domain.enums.ChallengeStatus;
import com.chess.backend.features.challenge.domain.enums.GameSide;
import com.chess.backend.features.challenge.repository.GameChallengeRepository;
import com.chess.backend.features.user.service.UserDirectory;
import com.chess.backend.features.auth.security.JwtService;
import com.chess.backend.features.social.service.FriendService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeService {
    private static final String ROOM_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final GameChallengeRepository challenges;
    private final UserDirectory users;
    private final FriendService friends;
    private final JwtService jwtService;
    private final SimpMessagingTemplate messaging;
    private final AppProperties properties;
    private final Clock clock;

    public ChallengeService(GameChallengeRepository challenges, UserDirectory users, FriendService friends,
                           JwtService jwtService, SimpMessagingTemplate messaging, AppProperties properties, Clock clock) {
        this.challenges = challenges; this.users = users; this.friends = friends; this.jwtService = jwtService;
        this.messaging = messaging; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public void send(long challengerId, SendRequest request) {
        User challenger = requireUser(challengerId);
        User challenged = users.findByUsername(request.toUsername()).filter(User::isEmailVerified)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (challengerId == challenged.getId()) throw new SelfChallengeException("You cannot challenge yourself");
        if (!friends.areFriendsAndNotBlocked(challengerId, challenged.getId())) throw new ChallengeRequiresFriendException("Challenges are limited to friends");
        if ((request.initialTimeSeconds() == null) != (request.incrementSeconds() == null)) throw new InvalidTimeControlException("Both clock fields are required for a timed game");
        if (request.initialTimeSeconds() != null && (request.initialTimeSeconds() > 2592000 || request.incrementSeconds() > 3600))
            throw new InvalidTimeControlException("Time control is outside supported bounds");
        Instant now = Instant.now(clock);
        if (challenges.hasPendingBetween(challengerId, challenged.getId(), now, ChallengeStatus.PENDING))
            throw new ChallengeAlreadyPendingException("A challenge is already pending");
        GameSide side = request.challengerSide() == null ? GameSide.RANDOM : request.challengerSide();
        GameChallenge challenge = challenges.save(new GameChallenge(challenger, challenged, request.initialTimeSeconds(),
                request.incrementSeconds(), side, now.plus(properties.invitationTtl())));
        messaging.convertAndSendToUser(challenged.getId().toString(), "/queue/challenges",
                new ChallengeEvent(challenge.getId(), ChallengeStatus.PENDING, challengerId, challenger.getUsername(), challenger.getDisplayName(),
                        request.initialTimeSeconds(), request.incrementSeconds(), side.name(), null, null, null, null, null));
    }

    @Transactional
    public void accept(long userId, long challengeId) {
        GameChallenge challenge = challenges.lockById(challengeId)
                .filter(c -> c.getChallenged().getId() == userId && c.getStatus() == ChallengeStatus.PENDING)
                .orElseThrow(() -> new ChallengeNotFoundException("Pending challenge not found"));
        if (challenge.getExpiresAt().isBefore(Instant.now(clock))) {
            challenge.setStatus(ChallengeStatus.EXPIRED);
            notifyStatus(challenge, userId, ChallengeStatus.EXPIRED);
            return;
        }
        if (!friends.areFriendsAndNotBlocked(challenge.getChallenger().getId(), challenge.getChallenged().getId()))
            throw new ChallengeUnavailableException("Challenge is unavailable");
        String roomCode = generateRoomCode();
        boolean challengerWhite = switch (challenge.getChallengerSide()) {
            case WHITE -> true;
            case BLACK -> false;
            case RANDOM -> RANDOM.nextBoolean();
        };
        challenge.accept(roomCode, challengerWhite ? GameSide.WHITE : GameSide.BLACK);
        sendMatch(challenge, challenge.getChallenger(), roomCode, challengerWhite ? "white" : "black");
        sendMatch(challenge, challenge.getChallenged(), roomCode, challengerWhite ? "black" : "white");
    }

    @Transactional
    public GameChallenge lockAcceptedRoom(String roomCode) {
        GameChallenge challenge = challenges.lockByRoomCode(roomCode)
                .orElseThrow(() -> new GameRoomNotFoundException("Game room not found"));
        if (challenge.getStatus() != ChallengeStatus.ACCEPTED) {
            throw new GameRoomInactiveException("Game room is not active");
        }
        return challenge;
    }

    @Transactional
    public void complete(GameChallenge challenge) {
        challenge.setStatus(ChallengeStatus.COMPLETED);
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
        challenges.expirePending(Instant.now(clock), ChallengeStatus.PENDING, ChallengeStatus.EXPIRED);
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
    private void sendMatch(GameChallenge challenge, User recipient, String roomCode, String color) {
        String token = jwtService.createRoomToken(roomCode, recipient.getId(), recipient.getDisplayName(), color,
                challenge.getInitialTimeSeconds(), challenge.getIncrementSeconds());
        String url = properties.engineWsUrl() + "?room=" + roomCode + "&token=" + token;
        messaging.convertAndSendToUser(recipient.getId().toString(), "/queue/challenges", new ChallengeEvent(challenge.getId(),
                ChallengeStatus.ACCEPTED, challenge.getChallenger().getId(), null, null, challenge.getInitialTimeSeconds(),
                challenge.getIncrementSeconds(), challenge.getChallengerSide().name(), roomCode, color.toUpperCase(Locale.ROOT), url, token, null));
    }
    private String generateRoomCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(6);
            for (int index = 0; index < 6; index++) code.append(ROOM_CHARACTERS.charAt(RANDOM.nextInt(ROOM_CHARACTERS.length())));
            if (!challenges.existsByRoomCode(code.toString())) return code.toString();
        }
        throw new IllegalStateException("Could not allocate a unique room code");
    }
    private User requireUser(long id) { return users.findById(id).filter(User::isEmailVerified).orElseThrow(() -> new UserNotFoundException("User not found")); }
}
