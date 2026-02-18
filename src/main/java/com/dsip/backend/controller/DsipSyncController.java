package com.dsip.backend.controller;

import com.dsip.backend.auth.CurrentUser;
import com.dsip.backend.dto.SyncRequest;
import com.dsip.backend.dto.SyncResponse;
import com.dsip.backend.entity.User;
import com.dsip.backend.service.DsipSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dsip/sync")
@RequiredArgsConstructor
@Slf4j
public class DsipSyncController {

    private final DsipSyncService syncService;

    @PostMapping
    public ResponseEntity<SyncResponse> sync(
            @CurrentUser User user,
            @Valid @RequestBody SyncRequest request) {

        log.info("Sync request received for tracker {} by user {}",
                request.getTrackerId(), user.getId());

        SyncResponse response = syncService.sync(request, user);

        return ResponseEntity.ok(response);
    }
}
