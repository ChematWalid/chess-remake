package com.chess.backend.game;

import com.chess.backend.config.ApiException;
import com.chess.backend.config.AppProperties;
import com.chess.backend.domain.entity.GameChallenge;
import com.chess.backend.domain.entity.GameHistory;
import com.chess.backend.domain.entity.User;
import com.chess.backend.repository.GameChallengeRepository;
import com.chess.backend.repository.GameHistoryRepository;
import com.chess.backend.repository.UserRepository;
import com.chess.backend.security.ChessPrincipal;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GameHistoryController {
    private final GameHistoryRepository histories;
    private final GameChallengeRepository challenges;
    private final UserRepository users;
    private final AppProperties properties;
    private final Clock clock;

    public GameHistoryController(GameHistoryRepository histories, GameChallengeRepository challenges, UserRepository users,
                                 AppProperties properties, Clock clock) {
        this.histories = histories; this.challenges = challenges; this.users = users; this.properties = properties; this.clock = clock;
    }

    @PostMapping("/internal/games/record")
    @Transactional
    ResponseEntity<Void> record(@RequestHeader(name = "X-Internal-Token", required = false) String secret,
                                @Valid @RequestBody RecordRequest request) {
        if (!constantTimeEquals(properties.internalToken(), secret)) throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid engine credentials");
        GameChallenge challenge = challenges.findByRoomCode(request.roomCode()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Game room not found"));
        if (!request.roomCode().equals(request.roomToken()) || !"ACCEPTED".equals(challenge.getStatus()))
            throw new ApiException(HttpStatus.CONFLICT, "Game room token or state is invalid");
        User white = "WHITE".equals(challenge.getChallengerColor()) ? challenge.getChallenger() : challenge.getChallenged();
        User black = white.getId().equals(challenge.getChallenger().getId()) ? challenge.getChallenged() : challenge.getChallenger();
        if (!white.getId().toString().equals(request.whitePlayerId()) || !black.getId().toString().equals(request.blackPlayerId()))
            throw new ApiException(HttpStatus.CONFLICT, "Game players do not match the assigned room");
        if (request.totalMoves() != request.moves().size() || request.fens().size() != request.moves().size() + 1)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Game replay data is inconsistent");
        User winner = request.winnerPlayerId() == null ? null : users.findById(parsePlayerId(request.winnerPlayerId()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Winner is not a player in this game"));
        if (winner != null && !winner.getId().equals(white.getId()) && !winner.getId().equals(black.getId()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Winner is not a player in this game");
        if (request.isDraw() != (winner == null)) throw new ApiException(HttpStatus.BAD_REQUEST, "Winner and draw result disagree");
        if (histories.findByRoomCode(request.roomCode()).isPresent()) return ResponseEntity.ok().build();
        String reason = normalizeReason(request.reasonCode() == null ? request.terminationReason() : request.reasonCode());
        GameHistory game = new GameHistory(challenge, request.roomCode(), white, black, white.getUsername(), black.getUsername(),
                winner, request.isDraw(), reason, challenge.getInitialTimeSeconds(), challenge.getIncrementSeconds(),
                request.totalMoves(), request.pgn(), request.moves(), request.fens(), Instant.now(clock), request.durationSeconds());
        histories.save(game);
        if (request.isDraw()) { white.recordResult("DRAW"); black.recordResult("DRAW"); }
        else if (winner.getId().equals(white.getId())) { white.recordResult("WIN"); black.recordResult("LOSS"); }
        else { black.recordResult("WIN"); white.recordResult("LOSS"); }
        challenge.setStatus("COMPLETED");
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/games/my-history")
    @Transactional(readOnly = true)
    PageResponse<GameSummary> mine(@AuthenticationPrincipal ChessPrincipal principal,
                                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return page(histories.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc(principal.userId(), principal.userId(), pageable(page, size)));
    }

    @GetMapping("/games/user/{userId}")
    @Transactional(readOnly = true)
    PageResponse<GameSummary> player(@PathVariable long userId, @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        if (!users.existsById(userId)) throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        return page(histories.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc(userId, userId, pageable(page, size)));
    }

    @GetMapping("/games/{id}")
    @Transactional(readOnly = true)
    GameDetail detail(@PathVariable long id) {
        return histories.findById(id).map(GameHistoryController::detail)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Game not found"));
    }

    @GetMapping(value = "/games/{id}/pgn", produces = "application/x-chess-pgn")
    @Transactional(readOnly = true)
    ResponseEntity<String> pgn(@PathVariable long id) {
        GameHistory game = histories.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Game not found"));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=game-" + id + ".pgn").body(game.getPgn());
    }

    private static Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid page or page size");
        return PageRequest.of(page, size);
    }
    private static PageResponse<GameSummary> page(Page<GameHistory> result) {
        return new PageResponse<>(result.getContent().stream().map(GameHistoryController::summary).toList(), result.getNumber(), result.getSize(), result.getTotalElements());
    }
    private static GameSummary summary(GameHistory game) {
        return new GameSummary(game.getId(), game.getWhitePlayer() == null ? game.getWhiteUsernameSnapshot() : game.getWhitePlayer().getUsername(),
                game.getBlackPlayer() == null ? game.getBlackUsernameSnapshot() : game.getBlackPlayer().getUsername(), game.isDraw(),
                game.getWinner() == null ? null : game.getWinner().getId(), game.getTerminationReason(), game.getTotalMoves(), game.getPlayedAt());
    }
    private static GameDetail detail(GameHistory game) {
        return new GameDetail(summary(game), game.getPgn(), game.getMoves(), game.getFens(), game.getInitialTimeSeconds(),
                game.getIncrementSeconds(), game.getDurationSeconds());
    }
    private static long parsePlayerId(String value) {
        try { return Long.parseLong(value); } catch (NumberFormatException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid winner player id"); }
    }
    private static boolean constantTimeEquals(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
    private static String normalizeReason(String reason) {
        if (reason == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid termination reason");
        String normalized = reason.toUpperCase(Locale.ROOT);
        if (!List.of("CHECKMATE", "RESIGNATION", "TIMEOUT", "STALEMATE", "DRAW_AGREED", "INSUFFICIENT_MATERIAL", "DRAW", "ABORTED").contains(normalized))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid termination reason");
        return normalized;
    }
    public record RecordRequest(@NotBlank String roomCode, @NotBlank String roomToken, @NotBlank String whitePlayerId,
                                @NotBlank String blackPlayerId, String winnerPlayerId, @NotNull Boolean isDraw,
                                @NotBlank String terminationReason, @JsonAlias("reason_code") String reasonCode,
                                @PositiveOrZero int totalMoves, @PositiveOrZero int durationSeconds, @NotNull String pgn,
                                @NotNull List<String> moves, @NotNull List<String> fens) { }
    public record PageResponse<T>(List<T> items, int page, int size, long totalElements) { }
    public record GameSummary(long id, String whiteUsername, String blackUsername, boolean draw, Long winnerPlayerId,
                              String terminationReason, int totalMoves, Instant playedAt) { }
    public record GameDetail(GameSummary summary, String pgn, List<String> moves, List<String> fens,
                             Integer initialTimeSeconds, Integer incrementSeconds, int durationSeconds) { }
}
