package com.dsip.backend.user;

import com.dsip.backend.auth.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final SessionService sessionService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal OAuth2User principal) {
        String email = principal.getAttribute("email");

        Optional<User> user = userService.findByEmail(email);

        return user.map(u -> ResponseEntity.ok(UserDto.fromEntity(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/sessions/count")
    public ResponseEntity<Map<String, Integer>> getActiveSessionCount(
            @AuthenticationPrincipal OAuth2User principal) {

        String email = principal.getAttribute("email");
        int count = sessionService.countActiveSessionsForUser(email);

        return ResponseEntity.ok(Map.of("activeSessions", count));
    }

    @PostMapping("/sessions/invalidate-all")
    public ResponseEntity<Map<String, String>> invalidateAllSessions(
            @AuthenticationPrincipal OAuth2User principal) {

        String email = principal.getAttribute("email");
        sessionService.invalidateAllSessionsForUser(email);

        return ResponseEntity.ok(Map.of("message", "All sessions invalidated"));
    }
}
