package com.chess.backend.challenge;

import com.chess.backend.challenge.ChallengeDtos.ChallengeEvent;
import com.chess.backend.challenge.ChallengeDtos.SendRequest;
import com.chess.backend.config.ApiException;
import com.chess.backend.config.AppProperties;
import com.chess.backend.domain.entity.GameChallenge;
import com.chess.backend.domain.entity.User;
import com.chess.backend.repository.GameChallengeRepository;
import com.chess.backend.repository.UserRepository;
import com.chess.backend.security.JwtService;
import com.chess.backend.social.FriendService;
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
    private final UserRepository users;
    private final FriendService friends;
    private final JwtService jwtService;
    private final SimpMessagingTemplate messaging;
    private final AppProperties properties;
    private final Clock clock;

    public ChallengeService(GameChallengeRepository challenges, UserRepository users, FriendService friends,
                           JwtService jwtService, SimpMessagingTemplate messaging, AppProperties properties, Clock clock) {
        this.challenges = challenges; this.users = users; this.friends = friends; this.jwtService = jwtService;
        this.messaging = messaging; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public void send(long challengerId, SendRequest request) {
        User challenger = requireUser(challengerId);
        User challenged = users.findByUsernameIgnoreCase(request.toUsername()).filter(User::isEmailVerified)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (challengerId == challenged.getId()) throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot challenge yourself");
        if (!friends.areFriendsAndNotBlocked(challengerId, challenged.getId())) throw new ApiException(HttpStatus.FORBIDDEN, "Challenges are limited to friends");
        if ((request.initialTimeSeconds() == null) != (request.incrementSeconds() == null)) throw new ApiException(HttpStatus.BAD_REQUEST, "Both clock fields are required for a timed game");
        if (request.initialTimeSeconds() != null && (request.initialTimeSeconds() > 2592000 || request.incrementSeconds() > 3600))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Time control is outside supported bounds");
        Instant now = Instant.now(clock);
        if (challenges.hasPendingBetween(challengerId, challenged.getId(), now)) throw new ApiException(HttpStatus.CONFLICT, "A challenge is already pending");
        String side = request.challengerSide() == null ? "RANDOM" : request.challengerSide().name();
        GameChallenge challenge = challenges.save(new GameChallenge(challenger, challenged, request.initialTimeSeconds(),
                request.incrementSeconds(), side, now.plus(properties.invitationTtl())));
        messaging.convertAndSendToUser(challenged.getId().toString(), "/queue/challenges",
                new ChallengeEvent(challenge.getId(), "PENDING", challengerId, challenger.getUsername(), challenger.getDisplayName(),
                        request.initialTimeSeconds(), request.incrementSeconds(), side, null, null, null, null, null));
    }

    @Transactional
    public void accept(long userId, long challengeId) {
        GameChallenge challenge = challenges.lockById(challengeId).filter(c -> c.getChallenged().getId() == userId && "PENDING".equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pending challenge not found"));
        if (challenge.getExpiresAt().isBefore(Instant.now(clock))) { challenge.setStatus("EXPIRED"); throw new ApiException(HttpStatus.GONE, "Challenge has expired"); }
        if (!friends.areFriendsAndNotBlocked(challenge.getChallenger().getId(), challenge.getChallenged().getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Challenge is unavailable");
        String roomCode = generateRoomCode();
        boolean challengerWhite = switch (challenge.getChallengerSide()) {
            case "WHITE" -> true; case "BLACK" -> false; default -> RANDOM.nextBoolean();
        };
        challenge.accept(roomCode, challengerWhite ? "WHITE" : "BLACK");
        sendMatch(challenge, challenge.getChallenger(), roomCode, challengerWhite ? "white" : "black");
        sendMatch(challenge, challenge.getChallenged(), roomCode, challengerWhite ? "black" : "white");
    }

    @Transactional public void decline(long userId, long challengeId) { changePending(challengeId, userId, false, "DECLINED"); }
    @Transactional public void cancel(long userId, long challengeId) { changePending(challengeId, userId, true, "CANCELED"); }
    @Scheduled(fixedDelay = 30000) @Transactional public void expirePending() { challenges.expirePending(Instant.now(clock)); }

    private void changePending(long id, long actor, boolean challengerOnly, String status) {
        GameChallenge challenge = challenges.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pending challenge not found"));
        boolean authorized = challengerOnly ? challenge.getChallenger().getId() == actor : challenge.getChallenged().getId() == actor;
        if (!authorized || !"PENDING".equals(challenge.getStatus())) throw new ApiException(HttpStatus.NOT_FOUND, "Pending challenge not found");
        challenge.setStatus(status);
        long recipient = challengerOnly ? challenge.getChallenged().getId() : challenge.getChallenger().getId();
        messaging.convertAndSendToUser(Long.toString(recipient), "/queue/challenges",
                new ChallengeEvent(challenge.getId(), status, actor, null, null, null, null, null, null, null, null, null, null));
    }
    private void sendMatch(GameChallenge challenge, User recipient, String roomCode, String color) {
        String token = jwtService.createRoomToken(roomCode, recipient.getId(), recipient.getDisplayName(), color,
                challenge.getInitialTimeSeconds(), challenge.getIncrementSeconds());
        String url = properties.engineWsUrl() + "?room=" + roomCode + "&token=" + token;
        messaging.convertAndSendToUser(recipient.getId().toString(), "/queue/challenges", new ChallengeEvent(challenge.getId(),
                "ACCEPTED", challenge.getChallenger().getId(), null, null, challenge.getInitialTimeSeconds(),
                challenge.getIncrementSeconds(), challenge.getChallengerSide(), roomCode, color.toUpperCase(Locale.ROOT), url, token, null));
    }
    private String generateRoomCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(6);
            for (int index = 0; index < 6; index++) code.append(ROOM_CHARACTERS.charAt(RANDOM.nextInt(ROOM_CHARACTERS.length())));
            if (!challenges.existsByRoomCode(code.toString())) return code.toString();
        }
        throw new IllegalStateException("Could not allocate a unique room code");
    }
    private User requireUser(long id) { return users.findById(id).filter(User::isEmailVerified).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found")); }
}
