package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 原生撤销重发 MyBatis mapper；所有关系型 SQL 均位于 XML。 */
@Mapper
public interface RunResendMapper {

    int insert(Map<String, Object> params);

    int update(Map<String, Object> params);

    int updateIfStatus(Map<String, Object> params);

    int updateIfStatusAndLease(Map<String, Object> params);

    Map<String, Object> findById(@Param("resendId") String resendId);

    Map<String, Object> findByOwnerAndClientRequestId(
            @Param("ownerUserId") String ownerUserId,
            @Param("clientRequestId") String clientRequestId);

    Map<String, Object> findBySourceRunId(@Param("sourceRunId") String sourceRunId);

    Map<String, Object> findByReplacementRunId(@Param("replacementRunId") String replacementRunId);

    Map<String, Object> findActiveBySession(@Param("sessionId") String sessionId);

    List<Map<String, Object>> findDue(@Param("now") Instant now, @Param("limit") int limit);

    Map<String, Object> findLatestUserTurn(@Param("sessionId") String sessionId);

    int insertSessionLock(
            @Param("sessionId") String sessionId,
            @Param("resendId") String resendId,
            @Param("ownerUserId") String ownerUserId,
            @Param("now") Instant now);

    int deleteSessionLock(@Param("sessionId") String sessionId, @Param("resendId") String resendId);

    long countSessionLocks(@Param("sessionId") String sessionId);

    List<String> findDispatchedSourceRunIds(@Param("sessionId") String sessionId);

    int detachFeedbackMessagesByRunId(@Param("runId") String runId);

    int deleteScopeSessionsByRunId(@Param("runId") String runId);

    int deleteScopesByRunId(@Param("runId") String runId);

    int deleteSessionMessagesByRunId(@Param("runId") String runId);

    int deleteRunEventsByRunId(@Param("runId") String runId);
}
