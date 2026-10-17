package com.chess.backend.features.user.controller;

import com.chess.backend.common.security.ChessPrincipal;
import com.chess.backend.features.user.dto.UserDraftRequest;
import com.chess.backend.features.user.dto.UserDraftView;
import com.chess.backend.features.user.service.UserDraftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/users/me/drafts")
public class UserDraftController {
    private final UserDraftService service;

    public UserDraftController(UserDraftService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserDraftView> getAll(@AuthenticationPrincipal ChessPrincipal principal) {
        return service.getAll(principal.userId());
    }

    @PutMapping("/{featureKey}")
    public UserDraftView save(@AuthenticationPrincipal ChessPrincipal principal,
                              @PathVariable @Pattern(regexp = "[a-z][a-z0-9-]{0,63}") String featureKey,
                              @Valid @RequestBody UserDraftRequest request) {
        return service.save(principal.userId(), featureKey, request.payload());
    }

    @DeleteMapping("/{featureKey}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal ChessPrincipal principal,
                                       @PathVariable @Pattern(regexp = "[a-z][a-z0-9-]{0,63}") String featureKey) {
        service.delete(principal.userId(), featureKey);
        return ResponseEntity.noContent().build();
    }
}
