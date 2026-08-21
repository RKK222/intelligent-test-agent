package com.enterprise.testagent.notification;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.notification.UserNotification;
import com.enterprise.testagent.domain.notification.UserNotificationActionType;
import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.notification.UserNotificationRepository;
import com.enterprise.testagent.domain.notification.UserNotificationStatus;
import com.enterprise.testagent.domain.notification.UserNotificationType;
import com.enterprise.testagent.domain.notification.UserNotificationView;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShare;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.sessionshare.SessionShareMembership;
import com.enterprise.testagent.domain.sessionshare.SessionShareMembershipStatus;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 通用站内通知查询、已读、生命周期与实时状态编排。 */
@Service
public class UserNotificationApplicationService {

    private static final Duration RETENTION = Duration.ofDays(90);
    private static final Duration RECONCILE_INTERVAL = Duration.ofSeconds(30);
    private static final Set<UserNotificationType> DISPOSE_TYPES = EnumSet.of(
            UserNotificationType.AGENT_CONFIG_DISPOSE_PENDING,
            UserNotificationType.AGENT_CONFIG_DISPOSE_SUCCEEDED,
            UserNotificationType.AGENT_CONFIG_DISPOSE_FAILED,
            UserNotificationType.AGENT_CONFIG_DISPOSE_SUPERSEDED);

    private final UserNotificationRepository repository;
    private final UserNotificationRealtimeHub realtimeHub;
    private final Clock clock;

    /** 生产环境统一使用 UTC 时钟。 */
    @Autowired
    public UserNotificationApplicationService(
            UserNotificationRepository repository,
            UserNotificationRealtimeHub realtimeHub) {
        this(repository, realtimeHub, Clock.systemUTC());
    }

    /** 测试构造器允许固定时钟验证到期与保留边界。 */
    UserNotificationApplicationService(
            UserNotificationRepository repository,
            UserNotificationRealtimeHub realtimeHub,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.realtimeHub = Objects.requireNonNull(realtimeHub, "realtimeHub must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** 分页读取当前用户通知，并同时返回不受筛选影响的全局未读数。 */
    public UserNotificationPage list(
            UserId recipientUserId,
            boolean unreadOnly,
            PageRequest pageRequest) {
        Instant now = clock.instant();
        PageResponse<UserNotificationView> page = repository.findPage(
                recipientUserId, unreadOnly, now, pageRequest);
        return new UserNotificationPage(page, repository.countUnread(recipientUserId, now));
    }

    /** 通用通知 ID 已读入口；只允许接收人操作，并保持重复调用幂等。 */
    @Transactional
    public void markRead(UserId recipientUserId, UserNotificationId notificationId, String traceId) {
        UserNotification notification = repository.findByIdForRecipient(notificationId, recipientUserId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "通知不存在"));
        if (notification.readAt() != null) {
            return;
        }
        if (notification.actionType() == UserNotificationActionType.SESSION_SHARE) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "分享通知需在分享访问成功后标记已读");
        }
        Instant now = clock.instant();
        if (repository.markReadById(notificationId, recipientUserId, now, traceId)) {
            publish(recipientUserId, notificationId, UserNotificationChangeType.READ, traceId, now);
        }
    }

    /**
     * 分享保存后同步成员通知：首次/重新加入创建，普通更新改快照，移除立即失效。
     */
    @Transactional
    public void syncSessionShare(
            SessionShare previous,
            SessionShare current,
            String sessionTitle,
            String actorUsername,
            boolean reactivated,
            String traceId) {
        Objects.requireNonNull(current, "current share must not be null");
        Instant now = clock.instant();
        if (reactivated && previous != null) {
            invalidateAction(
                    current.shareId(), null, "REACTIVATED", traceId, now);
        }

        for (SessionShareMembership membership : current.memberships()) {
            SessionShareMembership previousMembership = previous == null
                    ? null
                    : previous.membership(membership.userId()).orElse(null);
            if (membership.status() == SessionShareMembershipStatus.ACTIVE) {
                boolean newGrant = previousMembership == null
                        || previousMembership.status() == SessionShareMembershipStatus.REMOVED
                        || reactivated;
                if (newGrant) {
                    createSessionShareNotification(
                            current, membership, sessionTitle, actorUsername, traceId, now);
                } else {
                    updateSessionShareNotification(
                            current, membership, sessionTitle, actorUsername, traceId, now);
                }
            } else if (previousMembership != null
                    && previousMembership.status() == SessionShareMembershipStatus.ACTIVE) {
                invalidateAction(current.shareId(), membership.userId(), "REMOVED", traceId, now);
            }
        }
    }

    /** 分享取消时失效全部接收人的当前通知。 */
    @Transactional
    public void invalidateSessionShare(SessionShareId shareId, String reason, String traceId) {
        invalidateAction(shareId, null, reason, traceId, clock.instant());
    }

    /** 会话归档后失效该会话关联的全部分享通知。 */
    @Transactional
    public void invalidateSessionSharesBySession(SessionId sessionId, String reason, String traceId) {
        Instant now = clock.instant();
        List<UserId> recipients = repository.findActiveSessionShareRecipientsBySession(sessionId);
        int changed = repository.invalidateSessionSharesBySession(sessionId, reason, traceId, now);
        if (changed > 0) {
            publishRecipients(recipients, UserNotificationChangeType.INVALIDATED, traceId, now);
        }
    }

    /** 分享访问成功后按接收人和 shareId 标记已读；调用方可把失败降级为不阻断访问。 */
    @Transactional
    public void markSessionShareRead(UserId recipientUserId, SessionShareId shareId, String traceId) {
        Instant now = clock.instant();
        if (repository.markReadByAction(
                recipientUserId,
                UserNotificationActionType.SESSION_SHARE,
                shareId.value(),
                now,
                traceId)) {
            publish(recipientUserId, null, UserNotificationChangeType.READ, traceId, now);
        }
    }

    /**
     * 推进单个 rollout/用户的 dispose 通知状态；采用更新、幂等插入、并发重试更新保证单行演进。
     */
    @Transactional
    public void syncAgentConfigDispose(
            UserId recipientUserId,
            String rolloutId,
            UserNotificationType stateType,
            String traceId) {
        Objects.requireNonNull(recipientUserId, "recipientUserId must not be null");
        if (!DISPOSE_TYPES.contains(stateType)) {
            throw new IllegalArgumentException("stateType must be an Agent config dispose type");
        }
        String normalizedRolloutId = rolloutId == null ? "" : rolloutId.trim();
        if (normalizedRolloutId.isBlank()) {
            throw new IllegalArgumentException("rolloutId must not be blank");
        }
        Instant now = clock.instant();
        DisposeCopy copy = disposeCopy(stateType);
        UserNotification notification = new UserNotification(
                new UserNotificationId(RuntimeIdGenerator.userNotificationId()),
                recipientUserId,
                stateType,
                null,
                copy.title(),
                copy.body(),
                copy.actionType(),
                normalizedRolloutId,
                "AGENT_CONFIG_DISPOSE:" + normalizedRolloutId + ":" + recipientUserId.value(),
                UserNotificationStatus.ACTIVE,
                null,
                null,
                null,
                null,
                traceId,
                now,
                now);
        if (repository.updateByDedupKeyIfChanged(notification)) {
            publish(recipientUserId, null, UserNotificationChangeType.UPDATED, traceId, now);
            return;
        }
        if (repository.insert(notification)) {
            publish(recipientUserId, notification.notificationId(), UserNotificationChangeType.CREATED, traceId, now);
            return;
        }
        // 并发插入可能发生在第一次 UPDATE 之后；只在状态确实不同的情况下命中并广播。
        if (repository.updateByDedupKeyIfChanged(notification)) {
            publish(recipientUserId, null, UserNotificationChangeType.UPDATED, traceId, now);
        }
    }

    /** 按实例、目标版本和策略修订幂等创建用户主动更新/回退通知。 */
    @Transactional
    public void syncLocalClientUpdateAvailable(
            UserId recipientUserId,
            String clientInstanceId,
            String clientName,
            String currentVersion,
            String targetVersion,
            String direction,
            long policyRevision,
            String traceId) {
        Objects.requireNonNull(recipientUserId, "recipientUserId must not be null");
        String instanceId = boundedIdentifier(clientInstanceId, "clientInstanceId", 128);
        String target = boundedIdentifier(targetVersion, "targetVersion", 64);
        if (policyRevision < 1) {
            throw new IllegalArgumentException("policyRevision must be positive");
        }
        String normalizedDirection = "ROLLBACK".equals(direction) ? "ROLLBACK" : "UPDATE";
        Instant now = clock.instant();
        UserNotification notification = new UserNotification(
                new UserNotificationId(RuntimeIdGenerator.userNotificationId()),
                recipientUserId,
                UserNotificationType.LOCAL_CLIENT_UPDATE_AVAILABLE,
                null,
                normalizedDirection.equals("ROLLBACK") ? "本地客户端可回退" : "本地客户端可更新",
                truncate(safeText(clientName, "本机设备")
                        + " · 当前 " + safeText(currentVersion, "未知")
                        + " → 目标 " + target,
                        500),
                UserNotificationActionType.LOCAL_CLIENT_UPDATE,
                instanceId,
                "LOCAL_CLIENT_UPDATE:" + instanceId + ":" + target + ":" + policyRevision,
                UserNotificationStatus.ACTIVE,
                null,
                null,
                null,
                null,
                traceId,
                now,
                now);
        if (repository.reactivateByDedupKeyIfChanged(notification)) {
            publish(recipientUserId, null, UserNotificationChangeType.UPDATED, traceId, now);
        } else if (repository.insert(notification)) {
            publish(recipientUserId, notification.notificationId(), UserNotificationChangeType.CREATED, traceId, now);
        }
    }

    /** 用户点击陈旧通知时只精确失效该 ID；调用方通过独立事务门面提交。 */
    @Transactional
    public void invalidateLocalClientUpdateNotification(
            UserId recipientUserId,
            UserNotificationId notificationId,
            String reason,
            String traceId) {
        Instant now = clock.instant();
        if (repository.invalidateActiveById(
                notificationId,
                recipientUserId,
                boundedIdentifier(reason, "reason", 128),
                traceId,
                now)) {
            publish(recipientUserId, notificationId, UserNotificationChangeType.INVALIDATED, traceId, now);
        }
    }

    /** 仅在更新成功或策略恢复一致后关闭该实例全部待处理更新通知。 */
    @Transactional
    public void invalidateLocalClientUpdate(
            UserId recipientUserId,
            String clientInstanceId,
            String reason,
            String traceId) {
        Instant now = clock.instant();
        List<UserId> recipients = repository.findActiveRecipientsByAction(
                UserNotificationActionType.LOCAL_CLIENT_UPDATE, clientInstanceId, recipientUserId);
        int changed = repository.invalidateActiveByAction(
                UserNotificationActionType.LOCAL_CLIENT_UPDATE,
                clientInstanceId,
                recipientUserId,
                boundedIdentifier(reason, "reason", 128),
                traceId,
                now);
        if (changed > 0) {
            publishRecipients(recipients, UserNotificationChangeType.INVALIDATED, traceId, now);
        }
    }

    /**
     * 建立用户级当前状态流：首帧和每 30 秒从数据库校准，变化信号到达时立即刷新未读数。
     */
    public Flux<UserNotificationStreamUpdate> stream(UserId recipientUserId) {
        Mono<UserNotificationStreamUpdate> initial = snapshot(recipientUserId);
        Flux<UserNotificationStreamUpdate> changes = realtimeHub.events(recipientUserId)
                .concatMap(change -> Mono.fromCallable(() -> new UserNotificationStreamUpdate(
                                change.changeType(),
                                change.notificationId(),
                                repository.countUnread(recipientUserId, clock.instant()),
                                clock.instant()))
                        .subscribeOn(Schedulers.boundedElastic()));
        Flux<UserNotificationStreamUpdate> reconcile = Flux.interval(RECONCILE_INTERVAL)
                .concatMap(ignored -> snapshot(recipientUserId));
        return Flux.concat(initial, Flux.merge(changes, reconcile));
    }

    /** 默认每天清理严格早于 90 天保留边界的通知历史。 */
    @Scheduled(cron = "${test-agent.notification.retention-cron:0 55 3 * * *}")
    public void deleteExpiredNotifications() {
        repository.deleteCreatedBefore(clock.instant().minus(RETENTION));
    }

    private Mono<UserNotificationStreamUpdate> snapshot(UserId recipientUserId) {
        return Mono.fromCallable(() -> {
                    Instant now = clock.instant();
                    return new UserNotificationStreamUpdate(
                            UserNotificationChangeType.SNAPSHOT,
                            null,
                            repository.countUnread(recipientUserId, now),
                            now);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void createSessionShareNotification(
            SessionShare share,
            SessionShareMembership membership,
            String sessionTitle,
            String actorUsername,
            String traceId,
            Instant now) {
        // 同一接收人的旧代际先失效；唯一 dedupKey 再防止事务重试产生重复行。
        invalidateAction(share.shareId(), membership.userId(), "SUPERSEDED", traceId, now);
        UserNotification notification = new UserNotification(
                new UserNotificationId(RuntimeIdGenerator.userNotificationId()),
                membership.userId(),
                UserNotificationType.SESSION_SHARED,
                share.ownerUserId(),
                notificationTitle(actorUsername),
                notificationBody(sessionTitle, membership.canChat()),
                UserNotificationActionType.SESSION_SHARE,
                share.shareId().value(),
                "SESSION_SHARE:" + share.shareId().value() + ":" + share.version()
                        + ":" + membership.userId().value(),
                UserNotificationStatus.ACTIVE,
                null,
                share.expiresAt(),
                null,
                null,
                traceId,
                now,
                now);
        if (repository.insert(notification)) {
            publish(membership.userId(), notification.notificationId(),
                    UserNotificationChangeType.CREATED, traceId, now);
        }
    }

    private void updateSessionShareNotification(
            SessionShare share,
            SessionShareMembership membership,
            String sessionTitle,
            String actorUsername,
            String traceId,
            Instant now) {
        int changed = repository.updateActiveByAction(
                membership.userId(),
                UserNotificationActionType.SESSION_SHARE,
                share.shareId().value(),
                share.ownerUserId(),
                notificationTitle(actorUsername),
                notificationBody(sessionTitle, membership.canChat()),
                share.expiresAt(),
                traceId,
                now);
        if (changed == 0) {
            createSessionShareNotification(share, membership, sessionTitle, actorUsername, traceId, now);
            return;
        }
        publish(membership.userId(), null, UserNotificationChangeType.UPDATED, traceId, now);
    }

    private void invalidateAction(
            SessionShareId shareId,
            UserId recipientUserId,
            String reason,
            String traceId,
            Instant now) {
        List<UserId> recipients = repository.findActiveRecipientsByAction(
                UserNotificationActionType.SESSION_SHARE, shareId.value(), recipientUserId);
        int changed = repository.invalidateActiveByAction(
                UserNotificationActionType.SESSION_SHARE,
                shareId.value(),
                recipientUserId,
                reason,
                traceId,
                now);
        if (changed > 0) {
            publishRecipients(recipients, UserNotificationChangeType.INVALIDATED, traceId, now);
        }
    }

    private void publishRecipients(
            List<UserId> recipients,
            UserNotificationChangeType changeType,
            String traceId,
            Instant now) {
        Set<UserId> uniqueRecipients = new HashSet<>(recipients);
        uniqueRecipients.forEach(recipient -> publish(recipient, null, changeType, traceId, now));
    }

    private void publish(
            UserId recipient,
            UserNotificationId notificationId,
            UserNotificationChangeType changeType,
            String traceId,
            Instant now) {
        realtimeHub.publishAfterCommit(new UserNotificationChange(
                recipient, notificationId, changeType, traceId, now));
    }

    private String notificationTitle(String actorUsername) {
        return truncate(safeText(actorUsername, "同事") + " 向你分享了对话", 200);
    }

    private String notificationBody(String sessionTitle, boolean canChat) {
        return truncate(safeText(sessionTitle, "未命名会话") + " · " + (canChat ? "可对话" : "只读"), 500);
    }

    private DisposeCopy disposeCopy(UserNotificationType type) {
        return switch (type) {
            case AGENT_CONFIG_DISPOSE_PENDING -> new DisposeCopy(
                    "智能体配置正在更新",
                    "当前任务结束后会自动加载新配置。",
                    UserNotificationActionType.NONE);
            case AGENT_CONFIG_DISPOSE_SUCCEEDED -> new DisposeCopy(
                    "智能体配置更新成功",
                    "新配置已经加载，可以正常使用。",
                    UserNotificationActionType.NONE);
            case AGENT_CONFIG_DISPOSE_FAILED -> new DisposeCopy(
                    "智能体配置更新失败",
                    "新配置暂未加载，请重启智能体后再试。",
                    UserNotificationActionType.RESTART_OWN_PROCESS);
            case AGENT_CONFIG_DISPOSE_SUPERSEDED -> new DisposeCopy(
                    "这次配置更新已结束",
                    "已有更新的配置，这条通知不用处理。",
                    UserNotificationActionType.NONE);
            case SESSION_SHARED, LOCAL_CLIENT_UPDATE_AVAILABLE -> throw new IllegalArgumentException(
                    "SESSION_SHARED is not an Agent config dispose type");
        };
    }

    private String safeText(String value, String fallback) {
        String normalized = value == null ? "" : value.strip().replaceAll("[\\p{Cntrl}]", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String boundedIdentifier(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }

    /** 按 Unicode code point 截断，避免在代理对中间切断展示文本。 */
    private String truncate(String value, int maxCodePoints) {
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maxCodePoints - 1);
        return value.substring(0, end) + "…";
    }

    /** dispose 通知只保存固定安全文案和受控动作，不接收下游异常文本。 */
    private record DisposeCopy(
            String title,
            String body,
            UserNotificationActionType actionType) {
    }
}
