package com.enterprise.testagent.opencode.runtime.observability;

import com.enterprise.testagent.domain.user.UserId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Trace 正文查看与下载审计；只记录身份、动作和结果，不记录正文或物理路径。 */
@Component
public class TraceAccessAuditLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceAccessAuditLogger.class);

    public void record(UserId actor, String targetUserId, String traceId, String action, String result, String requestTraceId) {
        record(actor == null ? "UNKNOWN" : actor.value(), targetUserId, traceId, action, result, requestTraceId);
    }

    /** 鉴权失败时也保留访问尝试；未认证请求不伪造用户身份。 */
    public void recordDenied(
            Optional<UserId> actor,
            String traceId,
            String action,
            String requestTraceId) {
        record(actor.map(UserId::value).orElse("UNAUTHENTICATED"), null, traceId, action, "DENIED", requestTraceId);
    }

    private void record(
            String actorUserId,
            String targetUserId,
            String traceId,
            String action,
            String result,
            String requestTraceId) {
        LOGGER.info(
                "event=trace_content_access_audit actorUserId={} targetUserId={} targetTraceId={} action={} result={} traceId={}",
                actorUserId, targetUserId, traceId, action, result, requestTraceId);
    }
}
