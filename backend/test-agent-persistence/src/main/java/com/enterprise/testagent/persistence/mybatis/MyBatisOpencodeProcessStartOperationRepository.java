package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperation;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationStep;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 用户 opencode 进程初始化进度的 MyBatis Repository 实现。
 */
@Repository
public class MyBatisOpencodeProcessStartOperationRepository implements OpencodeProcessStartOperationRepository {

    private final OpencodeProcessStartOperationMapper mapper;

    /**
     * 注入 XML mapper；SQL 统一维护在 resources/mybatis。
     */
    public MyBatisOpencodeProcessStartOperationRepository(OpencodeProcessStartOperationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public OpencodeProcessStartOperation start(
            String operationId,
            UserId requestedBy,
            String agentId,
            String traceId,
            Instant now) {
        OpencodeProcessStartOperation operation = OpencodeProcessStartOperation.started(
                operationId,
                requestedBy,
                agentId,
                traceId,
                now);
        mapper.insert(toRow(operation));
        return operation;
    }

    @Override
    public OpencodeProcessStartOperation markStep(
            String operationId,
            OpencodeProcessStartOperationStep step,
            Instant now) {
        mapper.updateStep(
                operationId,
                OpencodeProcessStartOperationStatus.RUNNING.name(),
                step.name(),
                now);
        return findExisting(operationId);
    }

    @Override
    public OpencodeProcessStartOperation markSucceeded(
            String operationId,
            String processId,
            String serviceAddress,
            Instant now) {
        mapper.updateSucceeded(
                operationId,
                OpencodeProcessStartOperationStatus.SUCCEEDED.name(),
                OpencodeProcessStartOperationStep.COMPLETED.name(),
                processId,
                serviceAddress,
                now);
        return findExisting(operationId);
    }

    @Override
    public OpencodeProcessStartOperation markFailed(
            String operationId,
            OpencodeProcessStartOperationStep step,
            String errorCode,
            String errorMessage,
            Instant now) {
        mapper.updateFailed(
                operationId,
                OpencodeProcessStartOperationStatus.FAILED.name(),
                step.name(),
                errorCode,
                errorMessage,
                now);
        return findExisting(operationId);
    }

    @Override
    public Optional<OpencodeProcessStartOperation> findById(String operationId, UserId requestedBy) {
        return Optional.ofNullable(mapper.findById(operationId, requestedBy.value()))
                .map(this::toDomain);
    }

    private OpencodeProcessStartOperation findExisting(String operationId) {
        return Optional.ofNullable(mapper.findByOperationId(operationId))
                .map(this::toDomain)
                .orElseThrow();
    }

    private OpencodeProcessStartOperationRow toRow(OpencodeProcessStartOperation operation) {
        return new OpencodeProcessStartOperationRow(
                operation.operationId(),
                operation.requestedBy().value(),
                operation.agentId(),
                operation.status().name(),
                operation.currentStep().name(),
                operation.errorCode(),
                operation.errorMessage(),
                operation.processId(),
                operation.serviceAddress(),
                operation.traceId(),
                operation.createdAt(),
                operation.updatedAt());
    }

    private OpencodeProcessStartOperation toDomain(OpencodeProcessStartOperationRow row) {
        return new OpencodeProcessStartOperation(
                row.operationId(),
                new UserId(row.requestedByUserId()),
                row.agentId(),
                OpencodeProcessStartOperationStatus.valueOf(row.status()),
                OpencodeProcessStartOperationStep.valueOf(row.currentStep()),
                row.errorCode(),
                row.errorMessage(),
                row.processId(),
                row.serviceAddress(),
                row.traceId(),
                row.createdAt(),
                row.updatedAt());
    }
}
