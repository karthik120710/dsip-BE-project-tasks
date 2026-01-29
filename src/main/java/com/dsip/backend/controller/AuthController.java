package com.dsip.backend.controller;

import com.dsip.backend.dto.UserDto;
import com.dsip.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus(
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", false
            ));
        }

        String email = principal.getAttribute("email");
        var user = userService.findByEmail(email);

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "user", user.map(UserDto::fromEntity).orElse(null)
        ));
    }
}
