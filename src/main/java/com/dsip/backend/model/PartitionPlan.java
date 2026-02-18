package com.dsip.backend.model;

import lombok.Value;

@Value
public class PartitionPlan {
    int partitionIndex;
    int expectedLengthDays;
    double allocatedCapital;
}
