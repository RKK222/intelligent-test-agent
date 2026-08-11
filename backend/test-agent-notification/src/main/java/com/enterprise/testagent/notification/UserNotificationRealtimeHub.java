package com.enterprise.testagent.notification;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastHandler;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.user.UserId;
import java.util.LinkedHashMap;
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
 * 用户通知变化发布中心：本机直接 fan-out，事务提交后再通过既有服务器广播唤醒其它节点。
 */
@Component
public class UserNotificationRealtimeHub implements ServerBroadcastHandler {

    public static final String BROADCAST_TYPE = "user-notification.changed";
    private static final Logger LOGGER = LoggerFactory.getLogger(UserNotificationRealtimeHub.class);

    private final Sinks.Many<UserNotificationChange> changes = Sinks.many().multicast().directBestEffort();
    private final ServerBroadcastPublisher broadcastPublisher;
    private final BackendInstanceIdentity identity;

    public UserNotificationRealtimeHub(
            ServerBroadcastPublisher broadcastPublisher,
            BackendInstanceIdentity identity) {
        this.broadcastPublisher = Objects.requireNonNull(broadcastPublisher, "broadcastPublisher must not be null");
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
    }

    /**
     * 当前事务提交成功后发布；没有事务时立即发布，确保回滚不会产生幽灵通知。
     */
    public void publishAfterCommit(UserNotificationChange change) {
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

    /** 只向当前用户的连接暴露变化信号，正文由权威分页接口重新读取。 */
    public Flux<UserNotificationChange> events(UserId recipientUserId) {
        Objects.requireNonNull(recipientUserId, "recipientUserId must not be null");
        return changes.asFlux().filter(change -> recipientUserId.equals(change.recipientUserId()));
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
            UserId recipient = new UserId(requiredString(payload, "recipientUserId"));
            String notificationId = optionalString(payload, "notificationId");
            emitLocal(new UserNotificationChange(
                    recipient,
                    notificationId == null ? null : new UserNotificationId(notificationId),
                    UserNotificationChangeType.valueOf(requiredString(payload, "changeType")),
                    event.traceId(),
                    event.occurredAt()));
        } catch (RuntimeException exception) {
            LOGGER.warn("用户通知广播处理失败 eventId={} traceId={}", event.eventId(), event.traceId(), exception);
        }
    }

    private void publish(UserNotificationChange change) {
        emitLocal(change);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientUserId", change.recipientUserId().value());
        payload.put("changeType", change.changeType().name());
        if (change.notificationId() != null) {
            payload.put("notificationId", change.notificationId().value());
        }
        try {
            broadcastPublisher.publish(new ServerBroadcastEvent(
                    RuntimeIdGenerator.serverBroadcastEventId(),
                    BROADCAST_TYPE,
                    identity.instanceId(),
                    identity.linuxServerId(),
                    change.traceId(),
                    change.occurredAt(),
                    Map.copyOf(payload)));
        } catch (RuntimeException exception) {
            LOGGER.warn("用户通知广播发布失败 recipientUserId={} traceId={}",
                    change.recipientUserId().value(), change.traceId(), exception);
        }
    }

    private void emitLocal(UserNotificationChange change) {
        changes.tryEmitNext(change);
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        String value = optionalString(payload, key);
        if (value == null) {
            throw new IllegalArgumentException("通知广播载荷缺少字段 " + key);
        }
        return value;
    }

    private static String optionalString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
