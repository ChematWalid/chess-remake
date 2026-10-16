package com.chess.backend.features.challenge.controller;

import com.chess.backend.common.security.ChessPrincipal;
import com.chess.backend.features.challenge.dto.ChallengeDetail;
import com.chess.backend.features.challenge.dto.ChallengePage;
import com.chess.backend.features.challenge.service.ChallengeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {
    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @GetMapping("/incoming")
    ChallengePage incoming(@AuthenticationPrincipal ChessPrincipal principal,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size) {
        return challengeService.incoming(principal.userId(), page, size);
    }

    @GetMapping("/outgoing")
    ChallengePage outgoing(@AuthenticationPrincipal ChessPrincipal principal,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size) {
        return challengeService.outgoing(principal.userId(), page, size);
    }

    @GetMapping("/{challengeId}")
    ChallengeDetail detail(@AuthenticationPrincipal ChessPrincipal principal, @PathVariable long challengeId) {
        return challengeService.detail(principal.userId(), challengeId);
    }
}
