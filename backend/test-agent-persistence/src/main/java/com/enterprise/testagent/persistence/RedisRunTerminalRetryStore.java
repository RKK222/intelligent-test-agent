package com.enterprise.testagent.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunConversationSummary;
import com.enterprise.testagent.domain.run.RunDiffCounts;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunSummaryStatus;
import com.enterprise.testagent.domain.run.RunTerminalProjection;
import com.enterprise.testagent.domain.run.RunTerminalRetry;
import com.enterprise.testagent.domain.run.RunTerminalRetryState;
import com.enterprise.testagent.domain.run.RunTerminalRetryStore;
import com.enterprise.testagent.domain.run.TokenUsage;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import com.enterprise.testagent.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * PostgreSQL 终态事务的 Redis 安全重试实现。
 *
 * <p>每个 Run 的 JSON 只包含已清洗 {@link com.enterprise.testagent.domain.run.RunTerminalProjection}，
 * 全局 ZSET 只保存 runId 与下一次执行时间，不保存 prompt、回答、parts 或事件 payload。
 */
public class RedisRunTerminalRetryStore implements RunTerminalRetryStore {

    private static final String KEY_PREFIX = "test-agent:run-terminal-retry:{terminal-retry}:";
    private static final String DUE_INDEX_KEY = KEY_PREFIX + "due";

    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
            local currentJson = redis.call('GET', KEYS[1])
            if currentJson then
                local current = cjson.decode(currentJson)
                local incoming = cjson.decode(ARGV[1])
                local currentGeneration = tonumber(current.terminalProjectionVersion or 0)
                local incomingGeneration = tonumber(incoming.terminalProjectionVersion or 0)
                if incomingGeneration < currentGeneration then
                    return 0
                end
                if incomingGeneration == currentGeneration then
                    local currentSeq = tonumber(current.projection.lastEventSeq or 0)
                    local incomingSeq = tonumber(incoming.projection.lastEventSeq or 0)
                    if incomingSeq < currentSeq then
                        return 0
                    end
                    if incomingSeq == currentSeq then
                        local currentAttempts = tonumber(current.failedAttempts or 0)
                        local incomingAttempts = tonumber(incoming.failedAttempts or 0)
                        if incomingAttempts < currentAttempts then
                            return 0
                        end
                        if incomingAttempts == currentAttempts and ARGV[1] ~= currentJson then
                            return 0
                        end
                    end
                end
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> DELETE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current or current ~= ARGV[1] then
                return 0
            end
            local removed = redis.call('ZREM', KEYS[2], ARGV[2])
            local deleted = redis.call('DEL', KEYS[1])
            return removed + deleted
            """, Long.class);

    private static final DefaultRedisScript<Long> CLEAN_DANGLING_DUE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current then
                return 0
            end
            return redis.call('ZREM', KEYS[2], ARGV[1])
            """, Long.class);

    private static final DefaultRedisScript<Long> CLEAN_INVALID_DUE_SCRIPT = new DefaultRedisScript<>("""
            return redis.call('ZREM', KEYS[1], ARGV[1])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** 生产使用 UTC 时钟，记录自身的绝对到期时间决定 Redis TTL。 */
    public RedisRunTerminalRetryStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, Clock.systemUTC());
    }

    RedisRunTerminalRetryStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, Clock clock) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** record 与 due 固定同 slot，并由 Lua 一次发布，禁止暴露只写入一侧的中间态。 */
    @Override
    public void save(RunTerminalRetry retry) {
        Objects.requireNonNull(retry, "retry must not be null");
        String runId = retry.projection().runId().value();
        Duration ttl = Duration.between(clock.instant(), retry.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            delete(retry);
            return;
        }
        try {
            String encoded = objectMapper.writeValueAsString(StoredRetry.from(retry));
            redisTemplate.execute(
                    SAVE_SCRIPT,
                    List.of(recordKey(runId), DUE_INDEX_KEY),
                    encoded,
                    Long.toString(ttl.toMillis()),
                    Long.toString(retry.nextAttemptAt().toEpochMilli()),
                    runId);
        } catch (JsonProcessingException | RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<RunTerminalRetry> find(RunId runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        try {
            String encoded = redisTemplate.opsForValue().get(recordKey(runId.value()));
            if (encoded == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(encoded, StoredRetry.class).toDomain());
        } catch (JsonProcessingException | RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    /** 缺失记录只清理无敏感内容的悬空索引；损坏 JSON fail-closed，不静默丢弃终态。 */
    @Override
    public List<RunTerminalRetry> findDue(Instant now, int limit) {
        Objects.requireNonNull(now, "now must not be null");
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        try {
            Set<String> runIds = redisTemplate.opsForZSet().rangeByScore(
                    DUE_INDEX_KEY,
                    Double.NEGATIVE_INFINITY,
                    now.toEpochMilli(),
                    0,
                    limit);
            if (runIds == null || runIds.isEmpty()) {
                return List.of();
            }
            List<RunTerminalRetry> due = new ArrayList<>(runIds.size());
            for (String rawRunId : runIds) {
                RunId runId;
                try {
                    runId = new RunId(rawRunId);
                } catch (IllegalArgumentException exception) {
                    cleanupInvalidDue(rawRunId);
                    continue;
                }
                Optional<RunTerminalRetry> retry = find(runId);
                if (retry.isEmpty()) {
                    cleanupDanglingDue(rawRunId);
                    continue;
                }
                if (!retry.orElseThrow().nextAttemptAt().isAfter(now)) {
                    due.add(retry.orElseThrow());
                }
            }
            due.sort(Comparator.comparing(RunTerminalRetry::nextAttemptAt)
                    .thenComparing(item -> item.projection().runId().value()));
            return List.copyOf(due);
        } catch (RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public boolean delete(RunTerminalRetry expected) {
        Objects.requireNonNull(expected, "expected must not be null");
        try {
            String runId = expected.projection().runId().value();
            String encoded = objectMapper.writeValueAsString(StoredRetry.from(expected));
            Long deleted = redisTemplate.execute(
                    DELETE_SCRIPT,
                    List.of(recordKey(runId), DUE_INDEX_KEY),
                    encoded,
                    runId);
            return deleted != null && deleted > 0;
        } catch (JsonProcessingException | RuntimeException exception) {
            if (exception instanceof PlatformException platformException) {
                throw platformException;
            }
            throw unavailable(exception);
        }
    }

    /** 仅在 record 仍不存在时原子清理悬空 due；并发写入的新记录绝不能被自愈路径删除。 */
    private void cleanupDanglingDue(String runId) {
        redisTemplate.execute(
                CLEAN_DANGLING_DUE_SCRIPT,
                List.of(recordKey(runId), DUE_INDEX_KEY),
                runId);
    }

    /** 非法 member 不可能由正常 save 产生，只移除索引并保留任何异常 record 供诊断。 */
    private void cleanupInvalidDue(String runId) {
        redisTemplate.execute(
                CLEAN_INVALID_DUE_SCRIPT,
                List.of(DUE_INDEX_KEY),
                runId);
    }

    private static String recordKey(String runId) {
        return KEY_PREFIX + "record:" + runId;
    }

    private static PlatformException unavailable(Throwable cause) {
        return new PlatformException(
                ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                "Run 终态重试状态暂不可用",
                Map.of(),
                cause);
    }

    /**
     * 显式白名单 Redis 形状，避免 Jackson 把领域对象的派生 getter 当成字段，也从结构上阻止未来原文字段误入重试。
     */
    private record StoredRetry(
            StoredProjection projection,
            String state,
            int failedAttempts,
            Instant firstFailedAt,
            Instant nextAttemptAt,
            Instant expiresAt,
            long terminalProjectionVersion) {

        private static StoredRetry from(RunTerminalRetry retry) {
            return new StoredRetry(
                    StoredProjection.from(retry.projection()),
                    retry.state().name(),
                    retry.failedAttempts(),
                    retry.firstFailedAt(),
                    retry.nextAttemptAt(),
                    retry.expiresAt(),
                    retry.terminalProjectionVersion());
        }

        private RunTerminalRetry toDomain() {
            return new RunTerminalRetry(
                    projection.toDomain(),
                    RunTerminalRetryState.valueOf(state),
                    failedAttempts,
                    firstFailedAt,
                    nextAttemptAt,
                    expiresAt,
                    terminalProjectionVersion);
        }
    }

    /** 终态事务字段的完整安全白名单；不定义 prompt、parts、原始回答或事件字段。 */
    private record StoredProjection(
            String runId,
            String sessionId,
            String status,
            long expectedStatusVersion,
            String terminalSource,
            String terminalReasonCode,
            String safeErrorMessage,
            boolean remoteStopConfirmed,
            long lastEventSeq,
            Instant detailsExpiresAt,
            String rootRemoteSessionId,
            int diffProposed,
            int diffAccepted,
            int diffRejected,
            String lastRemoteMessageId,
            String lastRemotePartId,
            Long tokenInput,
            Long tokenOutput,
            Long tokenReasoning,
            Long tokenCacheRead,
            Long tokenCacheWrite,
            BigDecimal costUsd,
            String traceId,
            Instant updatedAt,
            String agentId,
            String sourceType,
            String sourceRefId,
            String senderUserId,
            List<StoredSummary> summaries) {

        private static StoredProjection from(RunTerminalProjection projection) {
            return new StoredProjection(
                    projection.runId().value(),
                    projection.sessionId().value(),
                    projection.status().name(),
                    projection.expectedStatusVersion(),
                    projection.terminalSource(),
                    projection.terminalReasonCode(),
                    projection.safeErrorMessage(),
                    projection.remoteStopConfirmed(),
                    projection.lastEventSeq(),
                    projection.detailsExpiresAt(),
                    projection.rootRemoteSessionId(),
                    projection.diffCounts().proposed(),
                    projection.diffCounts().accepted(),
                    projection.diffCounts().rejected(),
                    projection.lastRemoteMessageId(),
                    projection.lastRemotePartId(),
                    projection.tokenUsage().input(),
                    projection.tokenUsage().output(),
                    projection.tokenUsage().reasoning(),
                    projection.tokenUsage().cacheRead(),
                    projection.tokenUsage().cacheWrite(),
                    projection.costUsd(),
                    projection.traceId(),
                    projection.updatedAt(),
                    projection.agentId(),
                    projection.sourceType().name(),
                    projection.sourceRefId(),
                    projection.senderUserId() == null ? null : projection.senderUserId().value(),
                    projection.summaries().stream().map(StoredSummary::from).toList());
        }

        private RunTerminalProjection toDomain() {
            return new RunTerminalProjection(
                    new RunId(runId),
                    new SessionId(sessionId),
                    RunStatus.valueOf(status),
                    expectedStatusVersion,
                    terminalSource,
                    terminalReasonCode,
                    safeErrorMessage,
                    remoteStopConfirmed,
                    lastEventSeq,
                    detailsExpiresAt,
                    rootRemoteSessionId,
                    new RunDiffCounts(diffProposed, diffAccepted, diffRejected),
                    lastRemoteMessageId,
                    lastRemotePartId,
                    new TokenUsage(tokenInput, tokenOutput, tokenReasoning, tokenCacheRead, tokenCacheWrite),
                    costUsd,
                    traceId,
                    updatedAt,
                    agentId,
                    ConversationSourceType.valueOf(sourceType),
                    sourceRefId,
                    senderUserId == null ? null : new UserId(senderUserId),
                    summaries == null ? List.of() : summaries.stream().map(StoredSummary::toDomain).toList());
        }
    }

    /** 单条 USER/ASSISTANT 摘要的显式 Redis 形状。 */
    private record StoredSummary(
            String messageId,
            String role,
            String content,
            String summaryKey,
            int summaryVersion,
            String summaryStatus,
            Instant createdAt,
            String remoteMessageId) {

        private static StoredSummary from(RunConversationSummary summary) {
            return new StoredSummary(
                    summary.messageId().value(),
                    summary.role().name(),
                    summary.content(),
                    summary.summaryKey(),
                    summary.summaryVersion(),
                    summary.summaryStatus().name(),
                    summary.createdAt(),
                    summary.remoteMessageId());
        }

        private RunConversationSummary toDomain() {
            return new RunConversationSummary(
                    new SessionMessageId(messageId),
                    SessionMessageRole.valueOf(role),
                    content,
                    summaryKey,
                    summaryVersion,
                    RunSummaryStatus.valueOf(summaryStatus),
                    createdAt,
                    remoteMessageId);
        }
    }
}
