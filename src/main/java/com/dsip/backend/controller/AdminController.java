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
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
            @Valid @RequestBody AddWhitelistEmailRequest request) {

        WhitelistedEmail whitelistedEmail = whitelistService.addEmail(request.getEmail(), "admin");

        log.info("Email {} added to whitelist by admin", request.getEmail());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WhitelistDto.fromEntity(whitelistedEmail));
    }

    @PostMapping("/whitelist/bulk")
    public ResponseEntity<Map<String, Object>> addWhitelistedEmailsBulk(
            @RequestBody List<String> emails) {

        if (emails == null || emails.isEmpty()) {
            throw new IllegalArgumentException("Email list must not be empty");
        }

        List<String> invalidEmails = emails.stream()
                .filter(e -> e == null || e.isBlank() || !e.matches("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"))
                .toList();

        if (!invalidEmails.isEmpty()) {
            throw new IllegalArgumentException("Invalid email(s): " + invalidEmails);
        }

        List<WhitelistDto> added = new ArrayList<>();
        List<String> alreadyExists = new ArrayList<>();

        for (String email : emails) {
            if (whitelistService.isEmailWhitelisted(email)) {
                alreadyExists.add(email);
            } else {
                WhitelistedEmail created = whitelistService.addEmail(email, "admin");
                added.add(WhitelistDto.fromEntity(created));
            }
        }

        log.info("Bulk whitelist: {} added, {} already existed", added.size(), alreadyExists.size());

        return ResponseEntity.ok(Map.of(
                "added", added,
                "alreadyExists", alreadyExists,
                "totalAdded", added.size(),
                "totalAlreadyExists", alreadyExists.size()
        ));
    }

    @DeleteMapping("/whitelist/{email}")
    public ResponseEntity<Map<String, String>> removeWhitelistedEmail(@PathVariable String email) {
        boolean removed = whitelistService.removeEmail(email);

        if (removed) {
            log.info("Email {} removed from whitelist by admin", email);
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
