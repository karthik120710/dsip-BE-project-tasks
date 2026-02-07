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

        List<DsipTracker> findAllTrackersByUserId(@Param("userId") UUID userId,
                        @Param("statuses") List<Integer> statuses);

        List<DsipTracker> findAllTrackersWithStockByUserId(@Param("userId") UUID userId);

        boolean existsByUserIdAndStockId(@Param("userId") UUID userId, @Param("stockId") Integer stockId);

        int updateTracker(DsipTracker tracker);

        int updateTrackerStatus(@Param("trackerId") Integer trackerId, @Param("status") int status);

        void updateTrackerAggregates(@Param("trackerId") Integer trackerId,
                        @Param("executedAmount") Double executedAmount,
                        @Param("sharesBought") Double sharesBought,
                        @Param("activePartitionIndex") Integer activePartitionIndex);

        // Partition Operations
        int insertPartition(DsipPartition partition);

        List<DsipPartition> findPartitionsByTrackerId(@Param("trackerId") Integer trackerId);

        Optional<DsipPartition> findActivePartitionByTrackerId(@Param("trackerId") Integer trackerId);

        void updatePartition(DsipPartition partition);

        List<DsipPartition> findCompletedPartitions(@Param("trackerId") Integer trackerId);

        Optional<DsipPartition> findPartitionByTrackerIdAndIndex(@Param("trackerId") Integer trackerId,
                        @Param("partitionIndex") Integer partitionIndex);

        // Execution Operations
        int insertExecution(DsipExecution execution);

        List<DsipExecution> findExecutionsByTrackerId(@Param("trackerId") Integer trackerId,
                        @Param("limit") Integer limit);

        List<com.dsip.backend.dto.TrackerDetailsDto.HistoryItem> findExecutionHistoryByTrackerId(
                        @Param("trackerId") Integer trackerId, @Param("limit") Integer limit);

        List<DsipExecution> findExecutionsByPartitionId(@Param("partitionId") Integer partitionId);

        // Tracker Details DTO support
        DsipTracker findTrackerDetailsById(@Param("trackerId") Integer trackerId, @Param("userId") UUID userId);

        List<com.dsip.backend.dto.TrackerDetailsDto.HistoryItem> findExecutionHistoryByTrackerId(
                        @Param("trackerId") Integer trackerId);

        // Deletion Operations
        int deleteExecutionsByTrackerId(@Param("trackerId") Integer trackerId);

        int deletePartitionsByTrackerId(@Param("trackerId") Integer trackerId);

        int deleteTrackerById(@Param("trackerId") Integer trackerId);
}
