package com.chess.backend.features.game.controller;


import com.chess.backend.features.game.dto.GameRecordRequest;
import com.chess.backend.features.game.service.GameHistoryService;
import com.chess.backend.features.game.dto.GameHistoryDtos.GameDetail;
import com.chess.backend.features.game.dto.GameHistoryDtos.GameSummary;
import com.chess.backend.features.game.dto.GameHistoryDtos.PageResponse;
import com.chess.backend.common.security.ChessPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final GameHistoryService service;

    public GameHistoryController(GameHistoryService service) {
        this.service = service;
    }

    @PostMapping("/internal/games/record")
    ResponseEntity<Void> record(@RequestHeader(name = "X-Internal-Token", required = false) String secret,
                                @Valid @RequestBody GameRecordRequest request) {
        boolean created = service.record(secret, request);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).build();
    }

    @GetMapping("/games/my-history")
    PageResponse<GameSummary> mine(@AuthenticationPrincipal ChessPrincipal principal,
                                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.history(principal.userId(), page, size);
    }

    @GetMapping("/games/user/{userId}")
    PageResponse<GameSummary> player(@PathVariable long userId, @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        return service.playerHistory(userId, page, size);
    }

    @GetMapping("/games/{id}")
    GameDetail detail(@PathVariable long id) {
        return service.game(id);
    }

    @GetMapping(value = "/games/{id}/pgn", produces = "application/x-chess-pgn")
    ResponseEntity<String> pgn(@PathVariable long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=game-" + id + ".pgn")
                .body(service.pgn(id));
    }
}
