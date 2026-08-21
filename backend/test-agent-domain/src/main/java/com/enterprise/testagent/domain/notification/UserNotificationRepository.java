package com.enterprise.testagent.domain.notification;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 用户通知关系型持久化端口；业务模块不得直接依赖 MyBatis mapper。 */
public interface UserNotificationRepository {

    /** 按 dedupKey 幂等插入，返回是否新增。 */
    boolean insert(UserNotification notification);

    /**
     * 仅当 dedupKey 已存在且业务状态真实变化时更新单行通知，并清空已读时间；相同状态返回 false。
     */
    boolean updateByDedupKeyIfChanged(UserNotification notification);

    /** 重新激活同一 dedupKey 的已失效通知，并刷新其当前版本文案。 */
    boolean reactivateByDedupKeyIfChanged(UserNotification notification);

    /** 更新仍有效的受控动作通知快照，返回命中行数。 */
    int updateActiveByAction(
            UserId recipientUserId,
            UserNotificationActionType actionType,
            String actionTargetId,
            UserId actorUserId,
            String title,
            String body,
            Instant expiresAt,
            String traceId,
            Instant updatedAt);

    /** 查询当前持久状态仍有效的动作通知接收人，用于逐用户实时 fan-out。 */
    List<UserId> findActiveRecipientsByAction(
            UserNotificationActionType actionType,
            String actionTargetId,
            UserId recipientUserId);

    /** 查询某个会话分享关联的有效通知接收人。 */
    List<UserId> findActiveSessionShareRecipientsBySession(SessionId sessionId);

    /** 失效指定受控动作通知；recipient 为空时处理该动作的全部接收人。 */
    int invalidateActiveByAction(
            UserNotificationActionType actionType,
            String actionTargetId,
            UserId recipientUserId,
            String reason,
            String traceId,
            Instant invalidatedAt);

    /** 只失效接收人的精确通知 ID，避免旧通知动作关闭同实例的新通知。 */
    boolean invalidateActiveById(
            UserNotificationId notificationId,
            UserId recipientUserId,
            String reason,
            String traceId,
            Instant invalidatedAt);

    /** 会话归档后失效该会话全部分享通知。 */
    int invalidateSessionSharesBySession(
            SessionId sessionId,
            String reason,
            String traceId,
            Instant invalidatedAt);

    Optional<UserNotification> findByIdForRecipient(UserNotificationId notificationId, UserId recipientUserId);

    PageResponse<UserNotificationView> findPage(
            UserId recipientUserId,
            boolean unreadOnly,
            Instant now,
            PageRequest pageRequest);

    long countUnread(UserId recipientUserId, Instant now);

    boolean markReadById(
            UserNotificationId notificationId,
            UserId recipientUserId,
            Instant readAt,
            String traceId);

    boolean markReadByAction(
            UserId recipientUserId,
            UserNotificationActionType actionType,
            String actionTargetId,
            Instant readAt,
            String traceId);

    int deleteCreatedBefore(Instant cutoff);
}
