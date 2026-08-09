package com.enterprise.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 会话消息 MyBatis Mapper；关系型 SQL 统一位于 XML。 */
public interface SessionMessageMapper {

    int insert(@Param("row") SessionMessageRow row);

    int update(@Param("row") SessionMessageRow row);

    SessionMessageRow findById(@Param("messageId") String messageId);

    SessionMessageRow findBySessionAndRemoteMessage(
            @Param("sessionId") String sessionId,
            @Param("remoteMessageId") String remoteMessageId);

    List<SessionMessageRow> findBySession(
            @Param("sessionId") String sessionId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countBySession(@Param("sessionId") String sessionId);
}
