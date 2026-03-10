package com.dsip.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartitionExecutionPlan {
    private int partitionNumber;
    private int phaseNumber;
    private double allocatedAmount;
}
