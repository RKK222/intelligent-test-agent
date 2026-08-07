package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendId;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.run.RunResendStatus;
import com.enterprise.testagent.domain.run.RunResendSourceTurn;
import com.enterprise.testagent.domain.run.RunResendSourceTurnQuery;
import com.enterprise.testagent.domain.run.RunResendTrigger;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 原生撤销重发仓储的 MyBatis XML 适配器。 */
@Repository
public class MyBatisRunResendRepository implements RunResendRepository, RunResendSourceTurnQuery {

    private final RunResendMapper mapper;

    public MyBatisRunResendRepository(RunResendMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RunResend save(RunResend resend) {
        Map<String, Object> values = params(resend);
        if (mapper.findById(resend.resendId().value()) == null) {
            mapper.insert(values);
        } else {
            mapper.update(values);
        }
        return resend;
    }

    @Override
    public boolean saveIfStatus(RunResend resend, RunResendStatus expectedStatus) {
        Map<String, Object> values = params(resend);
        values.put("expectedStatus", expectedStatus.name());
        return mapper.updateIfStatus(values) == 1;
    }

    @Override
    public boolean saveIfStatusAndLease(
            RunResend resend,
            RunResendStatus expectedStatus,
            String expectedLeaseToken) {
        Map<String, Object> values = params(resend);
        values.put("expectedStatus", expectedStatus.name());
        values.put("expectedLeaseToken", expectedLeaseToken);
        return mapper.updateIfStatusAndLease(values) == 1;
    }

    @Override
    public Optional<RunResend> findById(RunResendId resendId) {
        return optional(mapper.findById(resendId.value()));
    }

    @Override
    public Optional<RunResend> findByOwnerAndClientRequestId(UserId owner, String clientRequestId) {
        return optional(mapper.findByOwnerAndClientRequestId(owner.value(), clientRequestId));
    }

    @Override
    public Optional<RunResend> findBySourceRunId(RunId sourceRunId) {
        return optional(mapper.findBySourceRunId(sourceRunId.value()));
    }

    @Override
    public Optional<RunResend> findByReplacementRunId(RunId replacementRunId) {
        return optional(mapper.findByReplacementRunId(replacementRunId.value()));
    }

    @Override
    public Optional<RunResend> findActiveBySession(SessionId sessionId) {
        return optional(mapper.findActiveBySession(sessionId.value()));
    }

    @Override
    public List<RunResend> findDue(Instant now, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        return mapper.findDue(now, limit).stream().map(this::resend).toList();
    }

    @Override
    public Optional<RunResendSourceTurn> findLatestUserTurn(SessionId sessionId) {
        Map<String, Object> row = mapper.findLatestUserTurn(sessionId.value());
        if (row == null) return Optional.empty();
        return Optional.of(new RunResendSourceTurn(
                new RunId(text(row, "runId")), text(row, "remoteMessageId")));
    }

    @Override
    public boolean insertSessionLock(SessionId sessionId, RunResendId resendId, UserId owner, Instant now) {
        return mapper.insertSessionLock(sessionId.value(), resendId.value(), owner.value(), now) == 1;
    }

    @Override
    public void deleteSessionLock(SessionId sessionId, RunResendId resendId) {
        mapper.deleteSessionLock(sessionId.value(), resendId.value());
    }

    @Override
    public boolean hasSessionLock(SessionId sessionId) {
        return mapper.countSessionLocks(sessionId.value()) > 0;
    }

    @Override
    public List<RunId> findDispatchedSourceRunIds(SessionId sessionId) {
        return mapper.findDispatchedSourceRunIds(sessionId.value()).stream().map(RunId::new).toList();
    }

    private Optional<RunResend> optional(Map<String, Object> row) {
        return Optional.ofNullable(row).map(this::resend);
    }

    private RunResend resend(Map<String, Object> row) {
        return new RunResend(
                new RunResendId(text(row, "resendId")),
                new SessionId(text(row, "sessionId")),
                new UserId(text(row, "ownerUserId")),
                new RunId(text(row, "sourceRunId")),
                new RunId(text(row, "replacementRunId")),
                text(row, "sourceRemoteMessageId"),
                text(row, "replacementRemoteMessageId"),
                RunResendTrigger.valueOf(text(row, "triggerType")),
                number(row, "totalAttempt").intValue(),
                number(row, "automaticAttempt").intValue(),
                number(row, "automaticLimit").intValue(),
                RunResendStatus.valueOf(text(row, "status")),
                instant(row, "executeAt"),
                text(row, "targetLinuxServerId"),
                text(row, "leaseToken"),
                instant(row, "leaseUntil"),
                text(row, "clientRequestId"),
                text(row, "traceId"),
                text(row, "safeErrorMessage"),
                instant(row, "createdAt"),
                instant(row, "updatedAt"));
    }

    private Map<String, Object> params(RunResend resend) {
        Map<String, Object> values = new HashMap<>();
        values.put("resendId", resend.resendId().value());
        values.put("sessionId", resend.sessionId().value());
        values.put("ownerUserId", resend.ownerUserId().value());
        values.put("sourceRunId", resend.sourceRunId().value());
        values.put("replacementRunId", resend.replacementRunId().value());
        values.put("sourceRemoteMessageId", resend.sourceRemoteMessageId());
        values.put("replacementRemoteMessageId", resend.replacementRemoteMessageId());
        values.put("triggerType", resend.trigger().name());
        values.put("totalAttempt", resend.totalAttempt());
        values.put("automaticAttempt", resend.automaticAttempt());
        values.put("automaticLimit", resend.automaticLimit());
        values.put("status", resend.status().name());
        values.put("executeAt", resend.executeAt());
        values.put("targetLinuxServerId", resend.targetLinuxServerId());
        values.put("leaseToken", resend.leaseToken());
        values.put("leaseUntil", resend.leaseUntil());
        values.put("clientRequestId", resend.clientRequestId());
        values.put("traceId", resend.traceId());
        values.put("safeErrorMessage", resend.safeErrorMessage());
        values.put("createdAt", resend.createdAt());
        values.put("updatedAt", resend.updatedAt());
        return values;
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    private static Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof LocalDateTime localDateTime) return localDateTime.toInstant(ZoneOffset.UTC);
        throw new IllegalArgumentException("unsupported instant value: " + value.getClass().getName());
    }
}
