package com.chess.backend.features.user.controller;

import com.chess.backend.common.security.ChessPrincipal;
import com.chess.backend.features.user.dto.UserSettingsView;
import com.chess.backend.features.user.service.UserSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/settings")
public class UserSettingsController {
    private final UserSettingsService service;

    public UserSettingsController(UserSettingsService service) { this.service = service; }

    @GetMapping
    public UserSettingsView get(@AuthenticationPrincipal ChessPrincipal principal) {
        return service.get(principal.userId());
    }

    @PutMapping
    public UserSettingsView update(@AuthenticationPrincipal ChessPrincipal principal,
                                   @Valid @RequestBody UserSettingsView request) {
        return service.update(principal.userId(), request);
    }
}
