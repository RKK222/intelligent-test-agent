package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatus;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatusRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 内部模型 provider 探活状态 MyBatis Repository。 */
@Repository
public class MyBatisInternalModelProbeStatusRepository implements InternalModelProbeStatusRepository {

    private final InternalModelObservabilityMapper mapper;

    public MyBatisInternalModelProbeStatusRepository(InternalModelObservabilityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsert(InternalModelProbeStatus status) {
        mapper.upsertProbeStatus(toRow(status));
    }

    @Override
    public List<InternalModelProbeStatus> findAll() {
        return mapper.findAllProbeStatus().stream().map(this::toDomain).toList();
    }

    private InternalModelProbeStatusRow toRow(InternalModelProbeStatus status) {
        return new InternalModelProbeStatusRow(
                status.providerId(),
                status.lastOutcome().name(),
                status.lastHttpStatus(),
                status.lastErrorClass(),
                status.lastDurationMillis(),
                status.lastProbedAt(),
                status.lastSuccessAt(),
                // 连续失败计数由 SQL 依据本次结果自动递增/归零，写入方不传递。
                0,
                status.traceId());
    }

    private InternalModelProbeStatus toDomain(InternalModelProbeStatusRow row) {
        return new InternalModelProbeStatus(
                row.providerId(),
                InternalModelCallOutcome.valueOf(row.lastOutcome()),
                row.lastHttpStatus(),
                row.lastErrorClass(),
                row.lastDurationMillis(),
                row.lastProbedAt(),
                row.lastSuccessAt(),
                row.consecutiveFailures(),
                row.traceId());
    }
}
