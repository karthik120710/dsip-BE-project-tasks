package com.dsip.backend.controller;

import com.dsip.backend.service.SessionService;
import com.dsip.backend.service.UserService;
import com.dsip.backend.entity.User;
import com.dsip.backend.dto.UserDto;
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

        User user = userService.findByEmail(email)
                .orElseThrow(() -> new com.dsip.backend.exception.UserNotFoundException(email));

        return ResponseEntity.ok(UserDto.fromEntity(user));
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
