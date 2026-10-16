package com.chess.backend.features.social.controller;


import com.chess.backend.features.social.service.FriendService;
import com.chess.backend.features.social.service.PresenceService;
import com.chess.backend.common.security.ChessPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService service;
    private final PresenceService presence;

    public FriendController(FriendService service, PresenceService presence) {
        this.service = service;
        this.presence = presence;
    }

    @GetMapping
    List<FriendView> friends(@AuthenticationPrincipal ChessPrincipal principal) {
        return service.friends(principal.userId()).stream().map(friend -> new FriendView(friend.id(), friend.username(),
                friend.displayName(), "/api/users/" + friend.id() + "/avatar", presence.status(friend.id()))).toList();
    }

    @GetMapping("/requests/incoming")
    List<RequestView> incoming(@AuthenticationPrincipal ChessPrincipal principal) {
        return service.incoming(principal.userId()).stream().map(FriendController::mapRequest).toList();
    }

    @GetMapping("/requests/outgoing")
    List<RequestView> outgoing(@AuthenticationPrincipal ChessPrincipal principal) {
        return service.outgoing(principal.userId()).stream().map(FriendController::mapRequest).toList();
    }

    @PostMapping("/request/{username}")
    ResponseEntity<Void> request(@AuthenticationPrincipal ChessPrincipal principal, @PathVariable String username) {
        service.request(principal.userId(), username);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/accept/{requestId}")
    ResponseEntity<Void> accept(@AuthenticationPrincipal ChessPrincipal principal, @PathVariable long requestId) {
        service.accept(principal.userId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/decline/{requestId}")
    ResponseEntity<Void> decline(@AuthenticationPrincipal ChessPrincipal principal, @PathVariable long requestId) {
        service.decline(principal.userId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{friendId}")
    ResponseEntity<Void> remove(@AuthenticationPrincipal ChessPrincipal principal, @PathVariable long friendId) {
        service.remove(principal.userId(), friendId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/block/{userId}")
    ResponseEntity<Void> block(@AuthenticationPrincipal ChessPrincipal principal, @PathVariable long userId) {
        service.block(principal.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/block/{userId}")
    ResponseEntity<Void> unblock(@AuthenticationPrincipal ChessPrincipal principal, @PathVariable long userId) {
        service.unblock(principal.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invite")
    InviteResponse createInvite(@AuthenticationPrincipal ChessPrincipal principal) {
        return new InviteResponse("/friend-invite/" + service.createInvite(principal.userId()));
    }

    @PostMapping("/invite/redeem")
    ResponseEntity<Void> redeem(@AuthenticationPrincipal ChessPrincipal principal, @Valid @RequestBody RedeemRequest request) {
        service.redeemInvite(principal.userId(), request.token());
        return ResponseEntity.noContent().build();
    }

    public record InviteResponse(String path) { }
    public record RedeemRequest(@NotBlank String token) { }
    public record FriendView(long id, String username, String displayName, String avatarUrl, String presence) { }
    public record RequestView(long requestId, long userId, String username, String displayName, String avatarUrl,
                              Instant createdAt) { }

    private static RequestView mapRequest(FriendService.RequestView request) {
        return new RequestView(request.requestId(), request.userId(), request.username(), request.displayName(),
                "/api/users/" + request.userId() + "/avatar", request.createdAt());
    }
}
