package com.dsip.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for tracker details response, including tracker info and all its
 * partitions.
 * Replaces the generic Map<String, Object> return type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackerDetailsDto {

    private DsipTrackerDto tracker;
    private List<DsipPartitionDto> partitions;
}
