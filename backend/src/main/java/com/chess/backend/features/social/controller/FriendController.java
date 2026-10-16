package com.chess.backend.features.social.controller;


import com.chess.backend.features.social.service.FriendService;
import com.chess.backend.features.social.dto.CreateInviteResponse;
import com.chess.backend.features.social.dto.FriendRequestSummary;
import com.chess.backend.features.social.dto.FriendRequestView;
import com.chess.backend.features.social.dto.FriendView;
import com.chess.backend.features.social.dto.RedeemInviteRequest;
import com.chess.backend.features.social.service.PresenceService;
import com.chess.backend.common.security.ChessPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
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
        return service.friends(principal.userId()).stream().map(friend -> new FriendView(friend.publicUserId(), friend.username(), friend.displayName(),
                "/api/users/" + friend.publicUserId() + "/avatar", presence.status(friend.userId()))).toList();
    }

    @GetMapping("/requests/incoming")
    List<FriendRequestView> incoming(@AuthenticationPrincipal ChessPrincipal principal) {
        return service.incoming(principal.userId()).stream().map(FriendController::mapRequest).toList();
    }

    @GetMapping("/requests/outgoing")
    List<FriendRequestView> outgoing(@AuthenticationPrincipal ChessPrincipal principal) {
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

    @DeleteMapping("/{publicUserId}")
    ResponseEntity<Void> remove(@AuthenticationPrincipal ChessPrincipal principal, @PathVariable UUID publicUserId) {
        service.remove(principal.userId(), publicUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/block/{publicUserId}")
    ResponseEntity<Void> block(@AuthenticationPrincipal ChessPrincipal principal, @PathVariable UUID publicUserId) {
        service.block(principal.userId(), publicUserId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/block/{publicUserId}")
    ResponseEntity<Void> unblock(@AuthenticationPrincipal ChessPrincipal principal, @PathVariable UUID publicUserId) {
        service.unblock(principal.userId(), publicUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invite")
    CreateInviteResponse createInvite(@AuthenticationPrincipal ChessPrincipal principal) {
        return new CreateInviteResponse("/friend-invite/" + service.createInvite(principal.userId()));
    }

    @PostMapping("/invite/redeem")
    ResponseEntity<Void> redeem(@AuthenticationPrincipal ChessPrincipal principal,
                                @NotNull @Valid @RequestBody RedeemInviteRequest request) {
        service.redeemInvite(principal.userId(), request.token());
        return ResponseEntity.noContent().build();
    }

    private static FriendRequestView mapRequest(FriendRequestSummary request) {
        return new FriendRequestView(request.requestId(), request.publicUserId(), request.username(), request.displayName(),
                request.status(), "/api/users/" + request.publicUserId() + "/avatar", request.createdAt());
    }
}
