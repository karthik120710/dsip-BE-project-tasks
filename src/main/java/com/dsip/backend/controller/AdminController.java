package com.dsip.backend.controller;

import com.dsip.backend.service.WhitelistService;
import com.dsip.backend.entity.WhitelistedEmail;
import com.dsip.backend.dto.WhitelistDto;
import com.dsip.backend.dto.AddWhitelistEmailRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final WhitelistService whitelistService;

    @GetMapping("/whitelist")
    public ResponseEntity<List<WhitelistDto>> getAllWhitelistedEmails() {
        List<WhitelistDto> emails = whitelistService.getAllWhitelistedEmails()
                .stream()
                .map(WhitelistDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(emails);
    }

    @PostMapping("/whitelist")
    public ResponseEntity<WhitelistDto> addWhitelistedEmail(
            @Valid @RequestBody AddWhitelistEmailRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        String addedBy = principal.getAttribute("email");

        WhitelistedEmail whitelistedEmail = whitelistService.addEmail(request.getEmail(), addedBy);

        log.info("Email {} added to whitelist by {}", request.getEmail(), addedBy);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WhitelistDto.fromEntity(whitelistedEmail));
    }

    @DeleteMapping("/whitelist/{email}")
    public ResponseEntity<Map<String, String>> removeWhitelistedEmail(
            @PathVariable String email,
            @AuthenticationPrincipal OAuth2User principal) {

        String removedBy = principal.getAttribute("email");

        boolean removed = whitelistService.removeEmail(email);

        if (removed) {
            log.info("Email {} removed from whitelist by {}", email, removedBy);
            return ResponseEntity.ok(Map.of("message", "Email removed from whitelist"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Email not found in whitelist"));
        }
    }

    @GetMapping("/whitelist/check/{email}")
    public ResponseEntity<Map<String, Boolean>> checkEmailWhitelisted(@PathVariable String email) {
        boolean isWhitelisted = whitelistService.isEmailWhitelisted(email);
        return ResponseEntity.ok(Map.of("whitelisted", isWhitelisted));
    }
}
