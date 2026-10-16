package com.chess.backend.game;

import com.chess.backend.config.ApiException;
import com.chess.backend.config.AppProperties;
import com.chess.backend.domain.entity.GameChallenge;
import com.chess.backend.domain.entity.GameHistory;
import com.chess.backend.domain.entity.User;
import com.chess.backend.domain.enums.GameResult;
import com.chess.backend.domain.enums.TerminationReason;
import com.chess.backend.repository.GameChallengeRepository;
import com.chess.backend.repository.GameHistoryRepository;
import com.chess.backend.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameHistoryService {
    private final GameHistoryRepository histories;
    private final GameChallengeRepository challenges;
    private final UserRepository users;
    private final AppProperties properties;
    private final Clock clock;
    public GameHistoryService(GameHistoryRepository histories, GameChallengeRepository challenges, UserRepository users,
                              AppProperties properties, Clock clock) {
        this.histories = histories; this.challenges = challenges; this.users = users; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public boolean record(String secret, GameRecordRequest request) {
        if (!constantTimeEquals(properties.internalToken(), secret)) throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid engine credentials");
        if (histories.findByRoomCode(request.roomCode()).isPresent()) return false;
        GameChallenge challenge = challenges.lockByRoomCode(request.roomCode()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Game room not found"));
        if (!request.roomCode().equals(request.roomToken()) || !"ACCEPTED".equals(challenge.getStatus()))
            throw new ApiException(HttpStatus.CONFLICT, "Game room token or state is invalid");
        User white = "WHITE".equals(challenge.getChallengerColor()) ? challenge.getChallenger() : challenge.getChallenged();
        User black = white.getId().equals(challenge.getChallenger().getId()) ? challenge.getChallenged() : challenge.getChallenger();
        if (!white.getId().toString().equals(request.whitePlayerId()) || !black.getId().toString().equals(request.blackPlayerId()))
            throw new ApiException(HttpStatus.CONFLICT, "Game players do not match the room assignment");
        if (request.totalMoves() != request.moves().size() || request.fens().size() != request.moves().size() + 1)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Game replay data is inconsistent");
        User winner = request.winnerPlayerId() == null ? null : findWinner(request.winnerPlayerId());
        if (winner != null && !winner.getId().equals(white.getId()) && !winner.getId().equals(black.getId()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Winner is not a player in this game");
        if (request.isDraw() != (winner == null)) throw new ApiException(HttpStatus.BAD_REQUEST, "Winner and draw result disagree");
        if (histories.findByRoomCode(request.roomCode()).isPresent()) return false;
        TerminationReason reason = normalizeReason(request.reasonCode() == null ? request.terminationReason() : request.reasonCode());
        histories.save(new GameHistory(challenge, request.roomCode(), white, black, white.getUsername(), black.getUsername(),
                winner, request.isDraw(), reason, challenge.getInitialTimeSeconds(), challenge.getIncrementSeconds(),
                request.totalMoves(), request.pgn(), request.moves(), request.fens(), Instant.now(clock), request.durationSeconds()));
        if (request.isDraw()) { white.recordResult(GameResult.DRAW); black.recordResult(GameResult.DRAW); }
        else if (winner.getId().equals(white.getId())) { white.recordResult(GameResult.WIN); black.recordResult(GameResult.LOSS); }
        else { black.recordResult(GameResult.WIN); white.recordResult(GameResult.LOSS); }
        challenge.setStatus("COMPLETED");
        return true;
    }

    private User findWinner(String rawId) {
        try { return users.findById(Long.parseLong(rawId)).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Winner is not a player in this game")); }
        catch (NumberFormatException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid winner player id"); }
    }
    private static boolean constantTimeEquals(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
    private static TerminationReason normalizeReason(String reason) {
        try { return TerminationReason.valueOf(reason.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException | NullPointerException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid termination reason"); }
    }
}
