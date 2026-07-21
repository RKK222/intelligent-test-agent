package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 夜间执行任务 MyBatis mapper；关系型 SQL 统一位于 XML。 */
@Mapper
public interface NightExecutionTaskMapper {

    Integer lockCreateRequest(@Param("lockKey") String lockKey);

    int updateTask(Map<String, Object> params);

    int updateTaskIfStatus(Map<String, Object> params);

    int claimTaskForDispatch(Map<String, Object> params);

    int updateDispatchIfAttempt(Map<String, Object> params);

    int renewDispatchLease(
            @Param("taskId") String taskId,
            @Param("attemptId") String attemptId,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int insertTask(Map<String, Object> params);

    Map<String, Object> findById(@Param("taskId") String taskId);

    Map<String, Object> findByOwnerAndClientRequestId(
            @Param("ownerUserId") String ownerUserId,
            @Param("clientRequestId") String clientRequestId);

    Map<String, Object> findPendingBySession(@Param("sessionId") String sessionId);

    Map<String, Object> findVisibleFailureBySession(
            @Param("ownerUserId") String ownerUserId,
            @Param("sessionId") String sessionId);

    List<Map<String, Object>> findPendingByOwner(
            @Param("ownerUserId") String ownerUserId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countPendingByOwner(@Param("ownerUserId") String ownerUserId);

    List<Map<String, Object>> findScheduledDue(@Param("now") Instant now, @Param("limit") int limit);

    List<Map<String, Object>> findScheduledWindowExpired(
            @Param("now") Instant now,
            @Param("limit") int limit);

    List<Map<String, Object>> findDispatchingLeaseExpiredBefore(
            @Param("cutoff") Instant cutoff,
            @Param("limit") int limit);

    List<Map<String, Object>> findDispatchingByOwner(
            @Param("backendProcessId") String backendProcessId,
            @Param("limit") int limit);

    List<Map<String, Object>> findTerminalBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    List<Map<String, Object>> reservationCounts(
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);

    int insertReservation(@Param("slotStart") Instant slotStart, @Param("now") Instant now);

    int reserveSlot(
            @Param("slotStart") Instant slotStart,
            @Param("capacity") int capacity,
            @Param("now") Instant now);

    int releaseSlot(@Param("slotStart") Instant slotStart, @Param("now") Instant now);

    int deleteReservationsBefore(@Param("cutoff") Instant cutoff);

    int insertSessionLock(
            @Param("sessionId") String sessionId,
            @Param("taskId") String taskId,
            @Param("ownerUserId") String ownerUserId,
            @Param("now") Instant now);

    int deleteSessionLock(@Param("sessionId") String sessionId, @Param("taskId") String taskId);

    long countSessionLocks(@Param("sessionId") String sessionId);

    int deleteTerminalIfUnchanged(
            @Param("taskId") String taskId,
            @Param("stateVersion") long stateVersion,
            @Param("cutoff") Instant cutoff);

    int deleteTask(@Param("taskId") String taskId);
}
