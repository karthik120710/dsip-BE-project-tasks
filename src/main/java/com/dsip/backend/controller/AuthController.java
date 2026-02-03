package com.dsip.backend.controller;

import com.dsip.backend.auth.CurrentUser;
import com.dsip.backend.dto.UserDto;
import com.dsip.backend.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus(
            @CurrentUser User user) {

        if (user == null) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", false));
        }

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "user", UserDto.fromEntity(user)));
    }
}
