package com.chess.backend.challenge;

import com.chess.backend.challenge.ChallengeDtos.SendRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class ChallengeWebSocketController {

    private final ChallengeService challengeService;

    public ChallengeWebSocketController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @MessageMapping("/challenge.send")
    public void send(@Valid SendRequest request, Principal principal) {
        challengeService.send(userId(principal), request);
    }

    @MessageMapping("/challenge.accept")
    public void accept(ChallengeAction action, Principal principal) {
        challengeService.accept(userId(principal), action.challengeId());
    }

    @MessageMapping("/challenge.decline")
    public void decline(ChallengeAction action, Principal principal) {
        challengeService.decline(userId(principal), action.challengeId());
    }

    @MessageMapping("/challenge.cancel")
    public void cancel(ChallengeAction action, Principal principal) {
        challengeService.cancel(userId(principal), action.challengeId());
    }

    private static long userId(Principal principal) {
        return Long.parseLong(principal.getName());
    }

    public record ChallengeAction(long challengeId) { }
}
