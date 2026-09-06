package com.f1telemetry.repository;

import com.f1telemetry.domain.AiUsageRecord;
import com.f1telemetry.domain.SimDriver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface AiUsageRecordRepository extends JpaRepository<AiUsageRecord, Long> {
    List<AiUsageRecord> findByDriverOrderByTimestampDesc(SimDriver driver);

    @Query("SELECT COALESCE(SUM(r.costUsd), 0) FROM AiUsageRecord r WHERE r.driver = :driver")
    double getTotalSpentByDriver(@Param("driver") SimDriver driver);

    @Query("SELECT r.modelName AS model, SUM(r.inputUnits) AS totalInput, SUM(r.outputUnits) AS totalOutput, SUM(r.costUsd) AS totalCost, COUNT(r) AS totalCalls " +
           "FROM AiUsageRecord r WHERE r.driver = :driver GROUP BY r.modelName")
    List<Map<String, Object>> getUsageGroupByModel(@Param("driver") SimDriver driver);
}
