package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户通知 MyBatis mapper；关系型 SQL 全部维护在 XML。 */
@Mapper
public interface UserNotificationMapper {

    int insertNotification(@Param("row") UserNotificationRow row);

    int updateByDedupKeyIfChanged(@Param("row") UserNotificationRow row);

    int updateActiveByAction(
            @Param("recipientUserId") String recipientUserId,
            @Param("actionType") String actionType,
            @Param("actionTargetId") String actionTargetId,
            @Param("actorUserId") String actorUserId,
            @Param("title") String title,
            @Param("body") String body,
            @Param("expiresAt") Instant expiresAt,
            @Param("traceId") String traceId,
            @Param("updatedAt") Instant updatedAt);

    List<String> findActiveRecipientsByAction(
            @Param("actionType") String actionType,
            @Param("actionTargetId") String actionTargetId,
            @Param("recipientUserId") String recipientUserId);

    List<String> findActiveSessionShareRecipientsBySession(@Param("sessionId") String sessionId);

    int invalidateActiveByAction(
            @Param("actionType") String actionType,
            @Param("actionTargetId") String actionTargetId,
            @Param("recipientUserId") String recipientUserId,
            @Param("reason") String reason,
            @Param("traceId") String traceId,
            @Param("invalidatedAt") Instant invalidatedAt);

    int invalidateSessionSharesBySession(
            @Param("sessionId") String sessionId,
            @Param("reason") String reason,
            @Param("traceId") String traceId,
            @Param("invalidatedAt") Instant invalidatedAt);

    UserNotificationRow findByIdForRecipient(
            @Param("notificationId") String notificationId,
            @Param("recipientUserId") String recipientUserId);

    List<UserNotificationViewRow> findPage(
            @Param("recipientUserId") String recipientUserId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("now") Instant now,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countPage(
            @Param("recipientUserId") String recipientUserId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("now") Instant now);

    long countUnread(@Param("recipientUserId") String recipientUserId, @Param("now") Instant now);

    int markReadById(
            @Param("notificationId") String notificationId,
            @Param("recipientUserId") String recipientUserId,
            @Param("readAt") Instant readAt,
            @Param("traceId") String traceId);

    int markReadByAction(
            @Param("recipientUserId") String recipientUserId,
            @Param("actionType") String actionType,
            @Param("actionTargetId") String actionTargetId,
            @Param("readAt") Instant readAt,
            @Param("traceId") String traceId);

    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
