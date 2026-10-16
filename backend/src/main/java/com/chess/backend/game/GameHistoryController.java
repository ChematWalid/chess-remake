package com.chess.backend.game;

import com.chess.backend.config.ApiException;
import com.chess.backend.domain.entity.GameHistory;
import com.chess.backend.repository.GameHistoryRepository;
import com.chess.backend.repository.UserRepository;
import com.chess.backend.security.ChessPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
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
    private final GameHistoryService service;
    private final UserRepository users;

    public GameHistoryController(GameHistoryRepository histories, GameHistoryService service, UserRepository users) {
        this.histories = histories; this.service = service; this.users = users;
    }

    @PostMapping("/internal/games/record")
    ResponseEntity<Void> record(@RequestHeader(name = "X-Internal-Token", required = false) String secret,
                                @Valid @RequestBody GameRecordRequest request) {
        boolean created = service.record(secret, request);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).build();
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
                game.getWinner() == null ? null : game.getWinner().getId(), game.getTerminationReason().name(), game.getTotalMoves(), game.getPlayedAt());
    }
    private static GameDetail detail(GameHistory game) {
        return new GameDetail(summary(game), game.getPgn(), game.getMoves(), game.getFens(), game.getInitialTimeSeconds(),
                game.getIncrementSeconds(), game.getDurationSeconds());
    }
    public record PageResponse<T>(List<T> items, int page, int size, long totalElements) { }
    public record GameSummary(long id, String whiteUsername, String blackUsername, boolean draw, Long winnerPlayerId,
                              String terminationReason, int totalMoves, Instant playedAt) { }
    public record GameDetail(GameSummary summary, String pgn, List<String> moves, List<String> fens,
                             Integer initialTimeSeconds, Integer incrementSeconds, int durationSeconds) { }
}
