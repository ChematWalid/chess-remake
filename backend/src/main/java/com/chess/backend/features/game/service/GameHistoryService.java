package com.chess.backend.features.game.service;

import com.chess.backend.features.game.exception.InvalidGameResultException;
import com.chess.backend.features.game.exception.InvalidPlayerIdException;
import com.chess.backend.features.game.exception.InvalidReplayDataException;
import com.chess.backend.features.game.exception.InvalidTerminationReasonException;
import com.chess.backend.features.game.exception.InvalidWinnerException;


import com.chess.backend.common.exception.InvalidPaginationException;
import com.chess.backend.common.exception.UserNotFoundException;
import com.chess.backend.features.game.exception.GameNotFoundException;
import com.chess.backend.features.game.exception.GameRoomMismatchException;
import com.chess.backend.features.game.exception.InvalidEngineCredentialsException;



import com.chess.backend.features.game.dto.GameRecordRequest;
import com.chess.backend.common.config.AppProperties;
import com.chess.backend.features.challenge.domain.GameChallenge;
import com.chess.backend.features.game.domain.GameHistory;
import com.chess.backend.features.user.domain.User;
import com.chess.backend.features.game.dto.GameHistoryDtos.GameDetail;
import com.chess.backend.features.game.dto.GameHistoryDtos.GameSummary;
import com.chess.backend.features.game.dto.GameHistoryDtos.PageResponse;
import com.chess.backend.features.game.domain.enums.GameResult;
import com.chess.backend.features.challenge.domain.enums.GameSide;
import com.chess.backend.features.game.domain.enums.TerminationReason;
import com.chess.backend.features.challenge.service.ChallengeService;
import com.chess.backend.features.game.repository.GameHistoryRepository;
import com.chess.backend.features.user.service.UserDirectory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameHistoryService {
    private final GameHistoryRepository histories;
    private final ChallengeService challenges;
    private final UserDirectory users;
    private final AppProperties properties;
    private final Clock clock;
    public GameHistoryService(GameHistoryRepository histories, ChallengeService challenges, UserDirectory users,
                              AppProperties properties, Clock clock) {
        this.histories = histories; this.challenges = challenges; this.users = users; this.properties = properties; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<GameSummary> history(long userId, int page, int size) {
        Pageable pageable = pageable(page, size);
        Page<GameHistory> results = histories.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc(
                userId, userId, pageable);
        return toPageResponse(results);
    }

    @Transactional(readOnly = true)
    public PageResponse<GameSummary> playerHistory(long userId, int page, int size) {
        if (!users.existsById(userId)) {
            throw new UserNotFoundException("User not found");
        }
        return history(userId, page, size);
    }

    @Transactional(readOnly = true)
    public GameDetail game(long gameId) {
        GameHistory game = histories.findById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found"));
        return toDetail(game);
    }

    @Transactional(readOnly = true)
    public String pgn(long gameId) {
        return histories.findById(gameId)
                .map(GameHistory::getPgn)
                .orElseThrow(() -> new GameNotFoundException("Game not found"));
    }

    @Transactional
    public boolean record(String secret, GameRecordRequest request) {
        if (!constantTimeEquals(properties.internalToken(), secret)) throw new InvalidEngineCredentialsException("Invalid engine credentials");
        if (histories.findByRoomCode(request.roomCode()).isPresent()) return false;
        GameChallenge challenge = challenges.lockAcceptedRoom(request.roomCode());
        if (!request.roomCode().equals(request.roomToken()))
            throw new GameRoomMismatchException("Game room token or state is invalid");
        User white = challenge.getChallengerColor() == GameSide.WHITE ? challenge.getChallenger() : challenge.getChallenged();
        User black = white.getId().equals(challenge.getChallenger().getId()) ? challenge.getChallenged() : challenge.getChallenger();
        if (!white.getId().toString().equals(request.whitePlayerId()) || !black.getId().toString().equals(request.blackPlayerId()))
            throw new GameRoomMismatchException("Game players do not match the room assignment");
        if (request.totalMoves() != request.moves().size() || request.fens().size() != request.moves().size() + 1)
            throw new InvalidReplayDataException("Game replay data is inconsistent");
        User winner = request.winnerPlayerId() == null ? null : findWinner(request.winnerPlayerId());
        if (winner != null && !winner.getId().equals(white.getId()) && !winner.getId().equals(black.getId()))
            throw new InvalidWinnerException("Winner is not a player in this game");
        if (request.isDraw() != (winner == null)) throw new InvalidGameResultException("Winner and draw result disagree");
        if (histories.findByRoomCode(request.roomCode()).isPresent()) return false;
        TerminationReason reason = normalizeReason(request.reasonCode() == null ? request.terminationReason() : request.reasonCode());
        histories.save(new GameHistory(challenge, request.roomCode(), white, black, white.getUsername(), black.getUsername(),
                winner, request.isDraw(), reason, challenge.getInitialTimeSeconds(), challenge.getIncrementSeconds(),
                request.totalMoves(), request.pgn(), request.moves(), request.fens(), Instant.now(clock), request.durationSeconds()));
        if (request.isDraw()) { white.recordResult(GameResult.DRAW); black.recordResult(GameResult.DRAW); }
        else if (winner.getId().equals(white.getId())) { white.recordResult(GameResult.WIN); black.recordResult(GameResult.LOSS); }
        else { black.recordResult(GameResult.WIN); white.recordResult(GameResult.LOSS); }
        challenges.complete(challenge);
        return true;
    }

    private User findWinner(String rawId) {
        try { return users.findById(Long.parseLong(rawId)).orElseThrow(() -> new InvalidWinnerException("Winner is not a player in this game")); }
        catch (NumberFormatException exception) { throw new InvalidPlayerIdException("Invalid winner player id"); }
    }

    private static Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new InvalidPaginationException("Invalid page or page size");
        }
        return PageRequest.of(page, size);
    }

    private static PageResponse<GameSummary> toPageResponse(Page<GameHistory> result) {
        List<GameSummary> items = result.getContent().stream().map(GameHistoryService::toSummary).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private static GameSummary toSummary(GameHistory game) {
        String whiteUsername = game.getWhitePlayer() == null
                ? game.getWhiteUsernameSnapshot() : game.getWhitePlayer().getUsername();
        String blackUsername = game.getBlackPlayer() == null
                ? game.getBlackUsernameSnapshot() : game.getBlackPlayer().getUsername();
        Long winnerId = game.getWinner() == null ? null : game.getWinner().getId();
        return new GameSummary(game.getId(), whiteUsername, blackUsername, game.isDraw(), winnerId,
                game.getTerminationReason().name(), game.getTotalMoves(), game.getPlayedAt());
    }

    private static GameDetail toDetail(GameHistory game) {
        return new GameDetail(toSummary(game), game.getPgn(), game.getMoves(), game.getFens(),
                game.getInitialTimeSeconds(), game.getIncrementSeconds(), game.getDurationSeconds());
    }
    private static boolean constantTimeEquals(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
    private static TerminationReason normalizeReason(String reason) {
        try { return TerminationReason.valueOf(reason.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException | NullPointerException exception) { throw new InvalidTerminationReasonException("Invalid termination reason"); }
    }
}
