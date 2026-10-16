package com.chess.backend.profile;

import com.chess.backend.profile.ProfileDtos.UpdateRequest;
import com.chess.backend.security.ChessPrincipal;
import com.chess.backend.storage.StoredFile;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
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

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping("/me")
    ProfileDtos.Profile me(@AuthenticationPrincipal ChessPrincipal principal) {
        return service.me(principal.userId());
    }

    @PutMapping("/me")
    ProfileDtos.Profile update(@AuthenticationPrincipal ChessPrincipal principal, @Valid @RequestBody UpdateRequest request) {
        return service.update(principal.userId(), request);
    }

    @PostMapping("/me/avatar")
    ResponseEntity<?> avatar(@AuthenticationPrincipal ChessPrincipal principal, @RequestParam("file") MultipartFile file) {
        service.uploadAvatar(principal.userId(), file);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/avatar")
    ResponseEntity<byte[]> getAvatar(@PathVariable long id) throws IOException {
        try (StoredFile file = service.avatar(id)) {
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                    .contentLength(file.size()).cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(file.input().readAllBytes());
        } catch (Exception exception) {
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Could not read avatar", exception);
        }
    }

    @GetMapping("/{username}/profile")
    ProfileDtos.Profile publicProfile(@PathVariable String username) {
        return service.publicProfile(username);
    }

    @GetMapping("/search")
    ProfileDtos.Page<ProfileDtos.SearchResult> search(@AuthenticationPrincipal ChessPrincipal principal,
                                                       @RequestParam String q,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return service.search(principal.userId(), q, page, size);
    }
}
