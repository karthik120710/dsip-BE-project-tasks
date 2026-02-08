package com.dsip.backend.controller;

import com.dsip.backend.auth.CurrentUser;
import com.dsip.backend.dto.DsipTrackerDto;
import com.dsip.backend.dto.DsipTrackerUpdateDto;
import com.dsip.backend.dto.DsipExecutionRequestDto;
import com.dsip.backend.dto.ExecutionResponseDto;
import com.dsip.backend.entity.User;
import com.dsip.backend.service.DsipTrackerService;
import com.dsip.backend.service.ExecutionService;
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
    private final ExecutionService executionService;

    @PostMapping
    public ResponseEntity<DsipTrackerDto> createTracker(
            @CurrentUser User user,
            @Valid @RequestBody DsipTrackerDto request) {

        DsipTrackerDto tracker = dsipTrackerService.createTracker(user.getId(), request);

        return ResponseEntity.ok(tracker);
    }

    @GetMapping
    public ResponseEntity<com.dsip.backend.dto.PortfolioResponseDto> getAllTrackers(
            @CurrentUser User user) {

        com.dsip.backend.dto.PortfolioResponseDto portfolio = dsipTrackerService.getAllTrackers(user.getId());
        return ResponseEntity.ok(portfolio);
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
    public ResponseEntity<com.dsip.backend.dto.TrackerDetailsDto> updateTracker(
            @CurrentUser User user,
            @PathVariable Integer trackerId,
            @Valid @RequestBody DsipTrackerUpdateDto request) {

        dsipTrackerService.updateTracker(trackerId, user.getId(), request);
        com.dsip.backend.dto.TrackerDetailsDto details = dsipTrackerService.getTrackerDetailsDto(
                trackerId, user.getId());
        return ResponseEntity.ok(details);
    }

    @PostMapping("/{trackerId}/execute")
    public ResponseEntity<ExecutionResponseDto> executeTracker(
            @CurrentUser User user,
            @PathVariable Integer trackerId,
            @Valid @RequestBody DsipExecutionRequestDto request) {

        ExecutionResponseDto result = executionService.executeTrade(trackerId, user.getId(), request);
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

    @GetMapping("/{trackerId}/partitions/{partitionIndex}")
    public ResponseEntity<com.dsip.backend.dto.PartitionDetailsDto> getPartitionDetails(
            @CurrentUser User user,
            @PathVariable Integer trackerId,
            @PathVariable Integer partitionIndex) {

        com.dsip.backend.dto.PartitionDetailsDto details = dsipTrackerService.getPartitionDetails(
                trackerId, partitionIndex, user.getId());
        return ResponseEntity.ok(details);
    }

    @DeleteMapping("/{trackerId}")
    public ResponseEntity<Map<String, String>> deleteTracker(
            @CurrentUser User user,
            @PathVariable Integer trackerId) {

        dsipTrackerService.deleteTracker(trackerId, user.getId());
        return ResponseEntity.ok(Map.of("message", "Tracker deleted successfully"));
    }
}
