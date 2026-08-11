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
            emitLocal(new SessionMessageChange(
                    new SessionId(requiredString(payload, "sessionId")),
                    new RunId(requiredString(payload, "runId")),
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
                            "runId", change.runId().value())));
        } catch (RuntimeException exception) {
            LOGGER.warn("会话消息广播发布失败 sessionId={} runId={} traceId={}",
                    change.sessionId().value(), change.runId().value(), change.traceId(), exception);
        }
    }

    private void emitLocal(SessionMessageChange change) {
        changes.tryEmitNext(change);
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException("会话消息广播载荷缺少字段 " + key);
    }

    /** 后端已提交的会话消息变化，不承载正文或模型输出。 */
    public record SessionMessageChange(
            SessionId sessionId,
            RunId runId,
            String traceId,
            Instant occurredAt) {

        public SessionMessageChange {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(runId, "runId must not be null");
            if (traceId == null || traceId.isBlank()) {
                throw new IllegalArgumentException("traceId must not be blank");
            }
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
