package com.enterprise.testagent.domain.opencodeprocess;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * 用户 opencode 进程启动进度端口，写入方和轮询查询方通过 operationId 共享状态。
 */
public interface OpencodeProcessStartOperationRepository {

    OpencodeProcessStartOperation start(
            String operationId,
            UserId requestedBy,
            String agentId,
            String traceId,
            Instant now);

    OpencodeProcessStartOperation markStep(
            String operationId,
            OpencodeProcessStartOperationStep step,
            Instant now);

    OpencodeProcessStartOperation markSucceeded(
            String operationId,
            String processId,
            String serviceAddress,
            Instant now);

    OpencodeProcessStartOperation markFailed(
            String operationId,
            OpencodeProcessStartOperationStep step,
            String errorCode,
            String errorMessage,
            Instant now);

    Optional<OpencodeProcessStartOperation> findById(String operationId, UserId requestedBy);
}
