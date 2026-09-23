package com.llmgateway.repository;

import com.llmgateway.entity.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UsageLogRepository extends JpaRepository<UsageLog, UUID> {

    long countByKeyId(UUID keyId);

    long countByKeyIdAndCachedTrue(UUID keyId);

    // COALESCE guards against SUM over zero rows, which SQL returns as NULL rather
    // than 0 — a key with no usage yet should report 0, not blow up on unboxing.
    @Query("SELECT COALESCE(SUM(u.tokens), 0) FROM UsageLog u WHERE u.keyId = :keyId")
    long sumTokensByKeyId(@Param("keyId") UUID keyId);

    @Query("SELECT COALESCE(SUM(u.estimatedCost), 0.0) FROM UsageLog u WHERE u.keyId = :keyId")
    double sumEstimatedCostByKeyId(@Param("keyId") UUID keyId);
}
