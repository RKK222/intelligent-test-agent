package com.enterprise.testagent.domain.nightexecution;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 夜间任务、会话锁和时段容量的统一事务端口。 */
public interface NightExecutionTaskRepository {
    void lockCreateRequest(UserId owner, String clientRequestId);
    NightExecutionTask save(NightExecutionTask task);
    boolean updateIfStatus(NightExecutionTask task, NightExecutionTaskStatus expectedStatus);
    boolean claimForScheduledRun(NightExecutionTask task, ScheduledTaskRunId expectedScheduledRunId);
    Optional<NightExecutionTask> findById(NightExecutionTaskId taskId);
    Optional<NightExecutionTask> findByScheduledTaskRunId(ScheduledTaskRunId taskRunId);
    Optional<NightExecutionTask> findByOwnerAndClientRequestId(UserId owner, String clientRequestId);
    Optional<NightExecutionTask> findPendingBySession(SessionId sessionId);
    Optional<NightExecutionTask> findVisibleFailureBySession(UserId owner, SessionId sessionId);
    PageResponse<NightExecutionTask> findPendingByOwner(UserId owner, PageRequest pageRequest);
    List<NightExecutionTask> findScheduledDueBefore(Instant cutoff, int limit);
    List<NightExecutionTask> findDispatchingBefore(Instant cutoff, int limit);
    List<NightExecutionTask> findTerminalBefore(Instant cutoff, int limit);
    Map<Instant, Integer> reservationCounts(Instant windowStart, Instant windowEnd);
    boolean reserveSlot(Instant slotStart, int capacity, Instant now);
    void releaseSlot(Instant slotStart, Instant now);
    int deleteReservationsBefore(Instant cutoff);
    boolean insertSessionLock(SessionId sessionId, NightExecutionTaskId taskId, UserId owner, Instant now);
    void deleteSessionLock(SessionId sessionId, NightExecutionTaskId taskId);
    boolean hasSessionLock(SessionId sessionId);
    void delete(NightExecutionTaskId taskId);
}
