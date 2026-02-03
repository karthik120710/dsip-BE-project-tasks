package com.dsip.backend.controller;

import com.dsip.backend.auth.CurrentUser;
import com.dsip.backend.dto.DsipTrackerDto;
import com.dsip.backend.dto.DsipTrackerUpdateDto;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.entity.User;
import com.dsip.backend.service.DsipTrackerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dsip-trackers")
@RequiredArgsConstructor
@Slf4j
public class DsipTrackerController {

    private final DsipTrackerService dsipTrackerService;

    @PostMapping
    public ResponseEntity<DsipTrackerDto> createTracker(
            @CurrentUser User user,
            @Valid @RequestBody DsipTrackerDto request) {

        DsipTracker tracker = dsipTrackerService.createTracker(user.getId(), request);

        return ResponseEntity.ok(DsipTrackerDto.fromEntity(tracker));
    }

    @GetMapping
    public ResponseEntity<List<DsipTrackerDto>> getAllTrackers(
            @CurrentUser User user) {

        List<DsipTracker> trackers = dsipTrackerService.getAllTrackers(user.getId());
        List<DsipTrackerDto> dtos = trackers.stream()
                .map(DsipTrackerDto::fromEntity)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{trackerId}")
    public ResponseEntity<com.dsip.backend.dto.TrackerDetailsDto> getTrackerDetails(
            @CurrentUser User user,
            @PathVariable Integer trackerId) {

        com.dsip.backend.dto.TrackerDetailsDto details = dsipTrackerService.getTrackerDetailsDto(
                trackerId, user.getId());
        return ResponseEntity.ok(details);
    }

    @PutMapping("/{trackerId}")
    public ResponseEntity<Map<String, String>> updateTracker(
            @CurrentUser User user,
            @PathVariable Integer trackerId,
            @Valid @RequestBody DsipTrackerUpdateDto request) {

        dsipTrackerService.updateTracker(trackerId, user.getId(), request);
        return ResponseEntity.ok(Map.of("status", "UPDATED"));
    }

    @PostMapping("/{trackerId}/execute")
    public ResponseEntity<Map<String, Object>> executeTracker(
            @CurrentUser User user,
            @PathVariable Integer trackerId,
            @Valid @RequestBody com.dsip.backend.dto.DsipExecutionRequestDto request) {

        Map<String, Object> result = dsipTrackerService.recordExecution(trackerId, user.getId(), request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{trackerId}/executions")
    public ResponseEntity<List<com.dsip.backend.dto.DsipExecutionDto>> getTrackerExecutions(
            @CurrentUser User user,
            @PathVariable Integer trackerId,
            @RequestParam(defaultValue = "6") Integer limit) {

        List<com.dsip.backend.dto.DsipExecutionDto> executions = dsipTrackerService.getRecentExecutions(trackerId,
                user.getId(), limit);
        return ResponseEntity.ok(executions);
    }
}
