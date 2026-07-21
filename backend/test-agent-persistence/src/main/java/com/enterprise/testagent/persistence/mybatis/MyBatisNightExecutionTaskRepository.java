package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** 夜间执行领域仓储的 MyBatis 适配器。 */
@Repository
public class MyBatisNightExecutionTaskRepository implements NightExecutionTaskRepository {

    private final NightExecutionTaskMapper mapper;

    public MyBatisNightExecutionTaskRepository(NightExecutionTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void lockCreateRequest(UserId owner, String clientRequestId) {
        mapper.lockCreateRequest(owner.value() + ":" + clientRequestId);
    }

    @Override
    public NightExecutionTask save(NightExecutionTask task) {
        Map<String, Object> params = params(task);
        if (mapper.updateTask(params) == 0) {
            mapper.insertTask(params);
        }
        return task;
    }

    @Override
    public boolean updateIfStatus(NightExecutionTask task, NightExecutionTaskStatus expectedStatus) {
        Map<String, Object> values = params(task);
        values.put("expectedStatus", expectedStatus.name());
        values.put("expectedStateVersion", task.stateVersion() - 1);
        return mapper.updateTaskIfStatus(values) == 1;
    }

    @Override
    public boolean claimForDispatch(NightExecutionTask task, String expectedTargetLinuxServerId) {
        Map<String, Object> values = params(task);
        values.put("expectedTargetLinuxServerId", expectedTargetLinuxServerId);
        values.put("expectedStateVersion", task.stateVersion() - 1);
        return mapper.claimTaskForDispatch(values) == 1;
    }

    @Override
    public boolean updateDispatchIfAttempt(NightExecutionTask task, String expectedAttemptId) {
        Map<String, Object> values = params(task);
        values.put("expectedAttemptId", expectedAttemptId);
        return mapper.updateDispatchIfAttempt(values) == 1;
    }

    @Override
    public boolean renewDispatchLease(
            NightExecutionTaskId taskId,
            String attemptId,
            Instant leaseUntil,
            Instant now) {
        return mapper.renewDispatchLease(taskId.value(), attemptId, leaseUntil, now) == 1;
    }

    @Override
    public Optional<NightExecutionTask> findById(NightExecutionTaskId taskId) {
        return optional(mapper.findById(taskId.value()));
    }

    @Override
    public Optional<NightExecutionTask> findByOwnerAndClientRequestId(UserId owner, String clientRequestId) {
        return optional(mapper.findByOwnerAndClientRequestId(owner.value(), clientRequestId));
    }

    @Override
    public Optional<NightExecutionTask> findPendingBySession(SessionId sessionId) {
        return optional(mapper.findPendingBySession(sessionId.value()));
    }

    @Override
    public Optional<NightExecutionTask> findVisibleFailureBySession(UserId owner, SessionId sessionId) {
        return optional(mapper.findVisibleFailureBySession(owner.value(), sessionId.value()));
    }

    @Override
    public PageResponse<NightExecutionTask> findPendingByOwner(UserId owner, PageRequest pageRequest) {
        List<NightExecutionTask> items = mapper.findPendingByOwner(
                        owner.value(), pageRequest.size(), pageRequest.offset())
                .stream().map(this::task).toList();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(),
                mapper.countPendingByOwner(owner.value()));
    }

    @Override
    public List<NightExecutionTask> findScheduledDue(Instant now, int limit) {
        return tasks(mapper.findScheduledDue(now, limit));
    }

    @Override
    public List<NightExecutionTask> findScheduledWindowExpired(Instant now, int limit) {
        return tasks(mapper.findScheduledWindowExpired(now, limit));
    }

    @Override
    public List<NightExecutionTask> findDispatchingLeaseExpiredBefore(Instant cutoff, int limit) {
        return tasks(mapper.findDispatchingLeaseExpiredBefore(cutoff, limit));
    }

    @Override
    public List<NightExecutionTask> findDispatchingByOwner(String backendProcessId, int limit) {
        return tasks(mapper.findDispatchingByOwner(backendProcessId, limit));
    }

    @Override
    public List<NightExecutionTask> findTerminalBefore(Instant cutoff, int limit) {
        return tasks(mapper.findTerminalBefore(cutoff, limit));
    }

    @Override
    public Map<Instant, Integer> reservationCounts(Instant windowStart, Instant windowEnd) {
        return mapper.reservationCounts(windowStart, windowEnd).stream().collect(Collectors.toMap(
                row -> instant(row, "slotStart"),
                row -> number(row, "reservedCount").intValue(),
                (left, right) -> right,
                LinkedHashMap::new));
    }

    @Override
    public boolean reserveSlot(Instant slotStart, int capacity, Instant now) {
        if (capacity < 1) {
            return false;
        }
        // ON CONFLICT 不会把 PostgreSQL 事务标为失败，并发首次占位可继续执行条件更新。
        mapper.insertReservation(slotStart, now);
        return mapper.reserveSlot(slotStart, capacity, now) == 1;
    }

    @Override
    public void releaseSlot(Instant slotStart, Instant now) {
        mapper.releaseSlot(slotStart, now);
    }

    @Override
    public int deleteReservationsBefore(Instant cutoff) {
        return mapper.deleteReservationsBefore(cutoff);
    }

    @Override
    public boolean insertSessionLock(SessionId sessionId, NightExecutionTaskId taskId, UserId owner, Instant now) {
        // 会话主键和 task 唯一键共同保证一个会话只有一个待执行夜间任务。
        return mapper.insertSessionLock(sessionId.value(), taskId.value(), owner.value(), now) == 1;
    }

    @Override
    public void deleteSessionLock(SessionId sessionId, NightExecutionTaskId taskId) {
        mapper.deleteSessionLock(sessionId.value(), taskId.value());
    }

    @Override
    public boolean hasSessionLock(SessionId sessionId) {
        return mapper.countSessionLocks(sessionId.value()) > 0;
    }

    @Override
    public boolean deleteTerminalIfUnchanged(
            NightExecutionTaskId taskId,
            long stateVersion,
            Instant cutoff) {
        return mapper.deleteTerminalIfUnchanged(taskId.value(), stateVersion, cutoff) == 1;
    }

    @Override
    public void delete(NightExecutionTaskId taskId) {
        mapper.deleteTask(taskId.value());
    }

    private Optional<NightExecutionTask> optional(Map<String, Object> row) {
        return Optional.ofNullable(row).map(this::task);
    }

    private List<NightExecutionTask> tasks(List<Map<String, Object>> rows) {
        return rows.stream().map(this::task).toList();
    }

    private NightExecutionTask task(Map<String, Object> row) {
        return new NightExecutionTask(
                new NightExecutionTaskId(text(row, "taskId")),
                new UserId(text(row, "ownerUserId")),
                new SessionId(text(row, "sessionId")),
                new WorkspaceId(text(row, "workspaceId")),
                text(row, "clientRequestId"), text(row, "sessionTitle"), text(row, "contentPreview"),
                text(row, "runInputJson"), NightExecutionTaskStatus.valueOf(text(row, "status")),
                instant(row, "slotStart"), instant(row, "slotEnd"), instant(row, "windowEnd"),
                text(row, "targetLinuxServerId"),
                value(row, "scheduledTaskRunId", ScheduledTaskRunId::new),
                value(row, "runId", RunId::new),
                number(row, "rolloverCount").intValue(), bool(row, "taskCreatedSession"),
                instant(row, "dispatchStartedAt"), text(row, "dispatchAttemptId"),
                text(row, "dispatchOwnerBackendProcessId"), instant(row, "dispatchLeaseUntil"),
                number(row, "stateVersion").longValue(), instant(row, "dismissedAt"),
                instant(row, "reservationReleasedAt"), text(row, "errorCode"), text(row, "errorMessage"),
                text(row, "traceId"), instant(row, "createdAt"), instant(row, "updatedAt"));
    }

    private Map<String, Object> params(NightExecutionTask task) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("taskId", task.taskId().value());
        values.put("ownerUserId", task.ownerUserId().value());
        values.put("sessionId", task.sessionId().value());
        values.put("workspaceId", task.workspaceId().value());
        values.put("clientRequestId", task.clientRequestId());
        values.put("sessionTitle", task.sessionTitle());
        values.put("contentPreview", task.contentPreview());
        values.put("runInputJson", task.runInputJson());
        values.put("status", task.status().name());
        values.put("slotStart", task.slotStart());
        values.put("slotEnd", task.slotEnd());
        values.put("windowEnd", task.windowEnd());
        values.put("targetLinuxServerId", task.targetLinuxServerId());
        values.put("scheduledTaskRunId", task.scheduledTaskRunId() == null ? null : task.scheduledTaskRunId().value());
        values.put("runId", task.runId() == null ? null : task.runId().value());
        values.put("rolloverCount", task.rolloverCount());
        values.put("taskCreatedSession", task.taskCreatedSession());
        values.put("dispatchStartedAt", task.dispatchStartedAt());
        values.put("dispatchAttemptId", task.dispatchAttemptId());
        values.put("dispatchOwnerBackendProcessId", task.dispatchOwnerBackendProcessId());
        values.put("dispatchLeaseUntil", task.dispatchLeaseUntil());
        values.put("stateVersion", task.stateVersion());
        values.put("dismissedAt", task.dismissedAt());
        values.put("reservationReleasedAt", task.reservationReleasedAt());
        values.put("errorCode", task.errorCode());
        values.put("errorMessage", task.errorMessage());
        values.put("traceId", task.traceId());
        values.put("createdAt", task.createdAt());
        values.put("updatedAt", task.updatedAt());
        return values;
    }

    private <T> T value(Map<String, Object> row, String key, Function<String, T> constructor) {
        String value = text(row, key);
        return value == null || value.isBlank() ? null : constructor.apply(value);
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    private boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Boolean booleanValue) return booleanValue;
        return value instanceof Number number && number.intValue() != 0;
    }

    private Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof LocalDateTime localDateTime) return localDateTime.toInstant(ZoneOffset.UTC);
        throw new PlatformException(ErrorCode.INTERNAL_ERROR, "夜间任务时间字段解析失败");
    }
}
