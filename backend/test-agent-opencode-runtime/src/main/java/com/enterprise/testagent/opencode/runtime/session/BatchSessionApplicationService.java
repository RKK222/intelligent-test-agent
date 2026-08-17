package com.enterprise.testagent.opencode.runtime.session;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.session.BatchSessionAttributionRepository;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRuntimeTarget;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 批量单项会话创建服务，保证用户级 itemRequestId 幂等并落批量归因。 */
@Service
public class BatchSessionApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchSessionApplicationService.class);

    private final SessionApplicationService sessionService;
    private final BatchSessionAttributionRepository attributionRepository;

    public BatchSessionApplicationService(
            SessionApplicationService sessionService,
            BatchSessionAttributionRepository attributionRepository) {
        this.sessionService = Objects.requireNonNull(sessionService);
        this.attributionRepository = Objects.requireNonNull(attributionRepository);
    }

    /** 先快速命中已有会话，再在事务级 advisory lock 下二次确认并创建。 */
    @Transactional
    public Session create(
            UserId userId,
            WorkspaceId workspaceId,
            String title,
            BatchContext batchContext,
            String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(batchContext, "batchContext must not be null");
        Session existing = findExisting(userId, batchContext.itemRequestId());
        if (existing != null) {
            LOGGER.info("Batch session reused, batchId={}, itemRequestId={}, status=REUSED, traceId={}",
                    batchContext.batchId(), batchContext.itemRequestId(), traceId);
            return existing;
        }

        attributionRepository.lockCreateRequest(userId, batchContext.itemRequestId());
        existing = findExisting(userId, batchContext.itemRequestId());
        if (existing != null) {
            LOGGER.info("Batch session reused after lock, batchId={}, itemRequestId={}, status=REUSED, traceId={}",
                    batchContext.batchId(), batchContext.itemRequestId(), traceId);
            return existing;
        }

        Session created = sessionService.createSession(userId, workspaceId, title, traceId);
        if (!attributionRepository.markBatch(
                created.sessionId(), userId, batchContext.batchId(), batchContext.itemRequestId())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "批量会话归因写入失败",
                    Map.of("itemRequestId", batchContext.itemRequestId()));
        }
        LOGGER.info("Batch session created, batchId={}, itemRequestId={}, status=CREATED, traceId={}",
                batchContext.batchId(), batchContext.itemRequestId(), traceId);
        return created;
    }

    private Session findExisting(UserId userId, String itemRequestId) {
        SessionId sessionId = attributionRepository.findSessionId(userId, itemRequestId).orElse(null);
        return sessionId == null ? null : sessionService.getSession(userId, sessionId, true);
    }

    /** HTTP 响应复用会话服务的向后兼容目标解析。 */
    public SessionRuntimeTarget runtimeTarget(SessionId sessionId) {
        return sessionService.runtimeTarget(sessionId);
    }
}
