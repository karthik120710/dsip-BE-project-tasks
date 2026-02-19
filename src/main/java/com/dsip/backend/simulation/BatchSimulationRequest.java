package com.dsip.backend.simulation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for the batch simulation endpoint.
 */
@Data
public class BatchSimulationRequest {

    @NotBlank(message = "CSV config path is required")
    private String csvConfigPath;
}
