package com.enterprise.testagent.opencode.runtime.session;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastHandler;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 会话消息变化发布中心：只广播安全身份字段，消息正文始终由平台消息接口权威读取。
 */
@Component
public class SessionMessageRealtimeHub implements ServerBroadcastHandler {

    public static final String BROADCAST_TYPE = "session-message.changed";
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionMessageRealtimeHub.class);

    private final Sinks.Many<SessionMessageChange> changes = Sinks.many().multicast().directBestEffort();
    private final ServerBroadcastPublisher broadcastPublisher;
    private final BackendInstanceIdentity identity;

    public SessionMessageRealtimeHub(
            ServerBroadcastPublisher broadcastPublisher,
            BackendInstanceIdentity identity) {
        this.broadcastPublisher = Objects.requireNonNull(
                broadcastPublisher, "broadcastPublisher must not be null");
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
    }

    /** 当前事务提交成功后发布；没有事务时立即发布，避免回滚产生幽灵刷新。 */
    public void publishAfterCommit(SessionMessageChange change) {
        Objects.requireNonNull(change, "change must not be null");
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(change);
                }
            });
            return;
        }
        publish(change);
    }

    /** 只向指定 Session 的连接暴露变化信号。 */
    public Flux<SessionMessageChange> events(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return changes.asFlux().filter(change -> sessionId.equals(change.sessionId()));
    }

    @Override
    public boolean supports(String type) {
        return BROADCAST_TYPE.equals(type);
    }

    /** 远端广播只落到本机 sink，不再次广播。 */
    @Override
    public void handle(ServerBroadcastEvent event) {
        if (!supports(event.type()) || broadcastPublisher.instanceId().equals(event.originInstanceId())) {
            return;
        }
        try {
            Map<String, Object> payload = event.payload();
            String replacementRunId = optionalString(payload, "replacementRunId");
            if (replacementRunId == null) {
                replacementRunId = requiredString(payload, "runId");
            }
            String sourceRunId = optionalString(payload, "sourceRunId");
            String revision = optionalString(payload, "revision");
            String changeType = optionalString(payload, "changeType");
            emitLocal(new SessionMessageChange(
                    new SessionId(requiredString(payload, "sessionId")),
                    new RunId(sourceRunId == null ? replacementRunId : sourceRunId),
                    new RunId(replacementRunId),
                    changeType == null
                            ? SessionMessageChangeType.RESEND_STARTED
                            : SessionMessageChangeType.valueOf(changeType),
                    revision == null ? event.occurredAt() : Instant.parse(revision),
                    event.traceId(),
                    event.occurredAt()));
        } catch (RuntimeException exception) {
            LOGGER.warn("会话消息广播处理失败 eventId={} traceId={}",
                    event.eventId(), event.traceId(), exception);
        }
    }

    private void publish(SessionMessageChange change) {
        emitLocal(change);
        try {
            broadcastPublisher.publish(new ServerBroadcastEvent(
                    RuntimeIdGenerator.serverBroadcastEventId(),
                    BROADCAST_TYPE,
                    identity.instanceId(),
                    identity.linuxServerId(),
                    change.traceId(),
                    change.occurredAt(),
                    Map.of(
                            "sessionId", change.sessionId().value(),
                            "sourceRunId", change.sourceRunId().value(),
                            "replacementRunId", change.replacementRunId().value(),
                            "runId", change.replacementRunId().value(),
                            "changeType", change.changeType().name(),
                            "revision", change.revision().toString())));
        } catch (RuntimeException exception) {
            LOGGER.warn("会话消息广播发布失败 sessionId={} replacementRunId={} traceId={}",
                    change.sessionId().value(), change.replacementRunId().value(), change.traceId(), exception);
        }
    }

    private void emitLocal(SessionMessageChange change) {
        changes.tryEmitNext(change);
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        String value = optionalString(payload, key);
        if (value != null) return value;
        throw new IllegalArgumentException("会话消息广播载荷缺少字段 " + key);
    }

    private static String optionalString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    /** 重发消息投影的提交阶段；恢复类型要求客户端重新读取源轮次。 */
    public enum SessionMessageChangeType {
        RESEND_RESERVED,
        RESEND_STARTED,
        RESEND_RESTORED
    }

    /** 后端已提交的会话消息变化，不承载正文或模型输出。 */
    public record SessionMessageChange(
            SessionId sessionId,
            RunId sourceRunId,
            RunId replacementRunId,
            SessionMessageChangeType changeType,
            Instant revision,
            String traceId,
            Instant occurredAt) {

        public SessionMessageChange {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(sourceRunId, "sourceRunId must not be null");
            Objects.requireNonNull(replacementRunId, "replacementRunId must not be null");
            Objects.requireNonNull(changeType, "changeType must not be null");
            Objects.requireNonNull(revision, "revision must not be null");
            if (traceId == null || traceId.isBlank()) {
                throw new IllegalArgumentException("traceId must not be blank");
            }
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
