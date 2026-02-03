package com.dsip.backend.mapper;

import com.dsip.backend.entity.DsipExecution;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface DsipTrackerMapper {

    // Tracker Operations
    int insertTracker(DsipTracker tracker);

    Optional<DsipTracker> findTrackerById(@Param("trackerId") Integer trackerId);

    List<DsipTracker> findAllTrackersByUserId(@Param("userId") UUID userId, @Param("statuses") List<Integer> statuses);

    boolean existsByUserIdAndStockSymbol(@Param("userId") UUID userId, @Param("stockSymbol") String stockSymbol);

    int updateTracker(DsipTracker tracker);

    // Soft delete (change status to PAUSED/DELETED)
    int updateTrackerStatus(@Param("trackerId") Integer trackerId, @Param("status") int status);

    // Partition Operations
    int insertPartition(DsipPartition partition);

    List<DsipPartition> findPartitionsByTrackerId(@Param("trackerId") Integer trackerId);

    Optional<DsipPartition> findActivePartitionByTrackerId(@Param("trackerId") Integer trackerId);

    // Execution Operations
    int insertExecution(DsipExecution execution);

    void updatePartitionMetrics(@Param("partitionId") Integer partitionId,
            @Param("amount") Integer amount,
            @Param("status") Integer status,
            @Param("endDate") java.time.LocalDate endDate,
            @Param("activeConvictionScore") Integer activeConvictionScore,
            @Param("totalLockinPercentageCount") Integer totalLockinPercentageCount,
            @Param("consistentGrowthCount") Integer consistentGrowthCount);

    List<DsipExecution> findExecutionsByTrackerId(@Param("trackerId") Integer trackerId, @Param("limit") Integer limit);
}
