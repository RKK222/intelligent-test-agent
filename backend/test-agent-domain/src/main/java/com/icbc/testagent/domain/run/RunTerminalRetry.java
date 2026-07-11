package com.icbc.testagent.domain.run;

import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL 终态事务失败后的安全重试记录。
 *
 * <p>本记录只能包含已经清洗和截断的 {@link RunTerminalProjection}，禁止加入 prompt、完整回答、
 * reasoning、工具输入输出、parts 或原始事件。失败次数从首次数据库失败计数，并据此计算下一次退避。
 */
public record RunTerminalRetry(
        RunTerminalProjection projection,
        RunTerminalRetryState state,
        int failedAttempts,
        Instant firstFailedAt,
        Instant nextAttemptAt,
        Instant expiresAt,
        long terminalProjectionVersion) {

    /** 终态详情和数据库重试都不得超过 24 小时。 */
    public static final Duration MAX_RETENTION = Duration.ofHours(24);

    public RunTerminalRetry {
        Objects.requireNonNull(projection, "projection must not be null");
        if (state != RunTerminalRetryState.TERMINAL_PENDING_DB) {
            throw new IllegalArgumentException("state must be TERMINAL_PENDING_DB");
        }
        if (failedAttempts < 1) {
            throw new IllegalArgumentException("failedAttempts must be positive");
        }
        if (terminalProjectionVersion < 0) {
            throw new IllegalArgumentException("terminalProjectionVersion must not be negative");
        }
        firstFailedAt = DomainValidation.requireInstant(firstFailedAt, "firstFailedAt");
        nextAttemptAt = DomainValidation.requireInstant(nextAttemptAt, "nextAttemptAt");
        expiresAt = DomainValidation.requireInstant(expiresAt, "expiresAt");
        if (!nextAttemptAt.isAfter(firstFailedAt)) {
            throw new IllegalArgumentException("nextAttemptAt must be after firstFailedAt");
        }
        if (!expiresAt.isAfter(firstFailedAt)) {
            throw new IllegalArgumentException("expiresAt must be after firstFailedAt");
        }
        if (expiresAt.isAfter(firstFailedAt.plus(MAX_RETENTION))) {
            throw new IllegalArgumentException("terminal retry retention must not exceed 24 hours");
        }
        if (projection.detailsExpiresAt().isAfter(firstFailedAt)
                && expiresAt.isAfter(projection.detailsExpiresAt())) {
            throw new IllegalArgumentException("terminal retry must not outlive run details");
        }
        if (nextAttemptAt.isAfter(expiresAt)) {
            throw new IllegalArgumentException("nextAttemptAt must not be after expiresAt");
        }
    }

    /** 兼容不关联同 Run outbox 的故障收敛投影和旧 Redis 重试记录。 */
    public RunTerminalRetry(
            RunTerminalProjection projection,
            RunTerminalRetryState state,
            int failedAttempts,
            Instant firstFailedAt,
            Instant nextAttemptAt,
            Instant expiresAt) {
        this(projection, state, failedAttempts, firstFailedAt, nextAttemptAt, expiresAt, 0L);
    }

    /** 首次关系型事务失败后，按 5 秒退避创建待落库记录。 */
    public static RunTerminalRetry pending(RunTerminalProjection projection, Instant failedAt) {
        return pending(projection, failedAt, 0L);
    }

    /** 首次数据库失败时关联终态 outbox version，成功重试后才能安全确认同一版终态。 */
    public static RunTerminalRetry pending(
            RunTerminalProjection projection,
            Instant failedAt,
            long terminalProjectionVersion) {
        Objects.requireNonNull(projection, "projection must not be null");
        Instant safeFailedAt = DomainValidation.requireInstant(failedAt, "failedAt");
        // 原始详情已经丢失/到期时仍允许安全控制面投影独立保留 24 小时；未来详情窗口仍是更早上限。
        Instant expiresAt = projection.detailsExpiresAt().isAfter(safeFailedAt)
                ? earlier(projection.detailsExpiresAt(), safeFailedAt.plus(MAX_RETENTION))
                : safeFailedAt.plus(MAX_RETENTION);
        Instant nextAttemptAt = safeFailedAt.plus(delayAfterFailure(1));
        if (!nextAttemptAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("terminal retry details expire before the first backoff completes");
        }
        return new RunTerminalRetry(
                projection,
                RunTerminalRetryState.TERMINAL_PENDING_DB,
                1,
                safeFailedAt,
                nextAttemptAt,
                expiresAt,
                terminalProjectionVersion);
    }

    /**
     * 本轮重试再次失败后计算下一次时间；剩余保留窗口容不下完整退避时直接停止，禁止缩短退避突击数据库。
     */
    public Optional<RunTerminalRetry> rescheduleAfterFailure(Instant failedAt) {
        Instant safeFailedAt = DomainValidation.requireInstant(failedAt, "failedAt");
        if (safeFailedAt.isBefore(nextAttemptAt)) {
            throw new IllegalArgumentException("failedAt must not be before the scheduled attempt");
        }
        int nextFailedAttempts = Math.addExact(failedAttempts, 1);
        Instant candidate = safeFailedAt.plus(delayAfterFailure(nextFailedAttempts));
        if (!candidate.isBefore(expiresAt)) {
            return Optional.empty();
        }
        return Optional.of(new RunTerminalRetry(
                projection,
                state,
                nextFailedAttempts,
                firstFailedAt,
                candidate,
                expiresAt,
                terminalProjectionVersion));
    }

    /** 到期时间本身不再允许访问详情或发起关系型重试。 */
    public boolean isExpired(Instant now) {
        Instant safeNow = DomainValidation.requireInstant(now, "now");
        return !safeNow.isBefore(expiresAt);
    }

    /** 失败次数对应 5s、15s、30s、1m、2m、5m，之后封顶 5m。 */
    public static Duration delayAfterFailure(int failedAttempts) {
        return switch (failedAttempts) {
            case 1 -> Duration.ofSeconds(5);
            case 2 -> Duration.ofSeconds(15);
            case 3 -> Duration.ofSeconds(30);
            case 4 -> Duration.ofMinutes(1);
            case 5 -> Duration.ofMinutes(2);
            default -> {
                if (failedAttempts < 1) {
                    throw new IllegalArgumentException("failedAttempts must be positive");
                }
                yield Duration.ofMinutes(5);
            }
        };
    }

    private static Instant earlier(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }
}
