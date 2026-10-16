package com.chess.backend.features.challenge.controller;


import com.chess.backend.features.challenge.service.ChallengeService;
import com.chess.backend.features.challenge.dto.ChallengeAction;
import com.chess.backend.features.challenge.dto.SendChallengeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

@Controller
@Validated
public class ChallengeWebSocketController {

    private final ChallengeService challengeService;

    public ChallengeWebSocketController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @MessageMapping("/challenge.send")
    public void send(@NotNull @Valid SendChallengeRequest request, Principal principal) {
        challengeService.send(userId(principal), request);
    }

    @MessageMapping("/challenge.accept")
    public void accept(@NotNull @Valid ChallengeAction action, Principal principal) {
        challengeService.accept(userId(principal), action.challengeId());
    }

    @MessageMapping("/challenge.decline")
    public void decline(@NotNull @Valid ChallengeAction action, Principal principal) {
        challengeService.decline(userId(principal), action.challengeId());
    }

    @MessageMapping("/challenge.cancel")
    public void cancel(@NotNull @Valid ChallengeAction action, Principal principal) {
        challengeService.cancel(userId(principal), action.challengeId());
    }

    private static long userId(Principal principal) {
        return Long.parseLong(principal.getName());
    }

}
