package com.chess.backend.features.user.controller;


import com.chess.backend.features.user.service.ProfileService;
import com.chess.backend.features.user.dto.SearchResult;
import com.chess.backend.features.user.dto.ProfilePage;
import com.chess.backend.features.user.dto.Profile;
import com.chess.backend.features.user.dto.ProfileUpdateRequest;
import com.chess.backend.common.security.ChessPrincipal;
import com.chess.backend.features.user.storage.StoredFile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping("/me")
    Profile me(@AuthenticationPrincipal ChessPrincipal principal) {
        return service.me(principal.userId());
    }

    @PutMapping("/me")
    Profile update(@AuthenticationPrincipal ChessPrincipal principal,
                   @NotNull @Valid @RequestBody ProfileUpdateRequest request) {
        return service.update(principal.userId(), request);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<?> avatar(@AuthenticationPrincipal ChessPrincipal principal, @RequestParam("file") MultipartFile file) {
        service.uploadAvatar(principal.userId(), file);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{publicId}/avatar")
    ResponseEntity<byte[]> getAvatar(@PathVariable UUID publicId) throws IOException {
        try (StoredFile file = service.avatar(publicId)) {
            ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(file.contentType()))
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                    .header("X-Content-Type-Options", "nosniff");
            if (file.size() >= 0) {
                response.contentLength(file.size());
            }
            return response.body(file.input().readAllBytes());
        }
    }

    @GetMapping("/{username}/profile")
    Profile publicProfile(@PathVariable String username) {
        return service.publicProfile(username);
    }

    @GetMapping("/search")
    ProfilePage<SearchResult> search(@AuthenticationPrincipal ChessPrincipal principal,
                                                       @RequestParam String q,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return service.search(principal.userId(), q, page, size);
    }
}
