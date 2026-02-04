package com.dsip.backend.mapper;

import com.dsip.backend.entity.DsipExecution;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface DsipTrackerMapper {

    // Tracker Operations
    int insertTracker(DsipTracker tracker);

    Optional<DsipTracker> findTrackerById(@Param("trackerId") Integer trackerId);

    List<DsipTracker> findAllTrackersByUserId(@Param("userId") UUID userId, @Param("statuses") List<Integer> statuses);

    boolean existsByUserIdAndStockId(@Param("userId") UUID userId, @Param("stockId") Integer stockId);

    int updateTracker(DsipTracker tracker);

    int updateTrackerStatus(@Param("trackerId") Integer trackerId, @Param("status") int status);

    void updateTrackerAggregates(@Param("trackerId") Integer trackerId,
            @Param("executedAmount") Integer executedAmount,
            @Param("sharesBought") Integer sharesBought,
            @Param("activePartitionIndex") Integer activePartitionIndex);

    // Partition Operations
    int insertPartition(DsipPartition partition);

    List<DsipPartition> findPartitionsByTrackerId(@Param("trackerId") Integer trackerId);

    Optional<DsipPartition> findActivePartitionByTrackerId(@Param("trackerId") Integer trackerId);

    void updatePartitionAfterExecution(@Param("partitionId") Integer partitionId,
            @Param("executedAmount") Integer executedAmount,
            @Param("sharesBought") Integer sharesBought,
            @Param("isGrowth") boolean isGrowth);

    void closePartition(@Param("partitionId") Integer partitionId, @Param("endDate") LocalDate endDate);

    List<Integer> findPartitionLengths(@Param("trackerId") Integer trackerId);

    // Execution Operations
    int insertExecution(DsipExecution execution);

    List<DsipExecution> findExecutionsByTrackerId(@Param("trackerId") Integer trackerId, @Param("limit") Integer limit);

    List<DsipExecution> findExecutionsByPartitionId(@Param("partitionId") Integer partitionId);
}
