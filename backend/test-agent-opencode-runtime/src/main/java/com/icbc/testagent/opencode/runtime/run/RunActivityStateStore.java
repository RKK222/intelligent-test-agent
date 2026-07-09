package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Run 运行态 Redis 状态存储。仅保存收敛判断需要的轻量状态，不替代 DB RunEvent 和消息快照。
 */
@Component
class RunActivityStateStore {

    static final Duration OUTPUT_ACTIVITY_TTL = Duration.ofMinutes(30);

    private static final Logger LOGGER = LoggerFactory.getLogger(RunActivityStateStore.class);
    private static final String OUTPUT_ACTIVITY_KEY_PREFIX = "test-agent:run-output-activity:";
    private static final String PENDING_ASK_KEY_PREFIX = "test-agent:run-pending-ask:";

    private final StringRedisTemplate redisTemplate;

    RunActivityStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 记录当前 Run 最近一次用户可见输出。TTL 存在即代表 30 分钟内仍有输出活动。
     */
    void recordOutput(RunId runId, Instant occurredAt) {
        if (redisTemplate == null || runId == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    outputActivityKey(runId),
                    Objects.requireNonNullElseGet(occurredAt, Instant::now).toString(),
                    OUTPUT_ACTIVITY_TTL);
        } catch (RuntimeException exception) {
            LOGGER.debug("Run output activity Redis write unavailable, runId={}", runId.value(), exception);
        }
    }

    /**
     * 判断当前 Run 30 分钟内是否仍有输出。Redis 不可用时保守返回 true，避免误杀运行中的会话。
     */
    boolean hasRecentOutput(RunId runId) {
        if (redisTemplate == null || runId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(outputActivityKey(runId)));
        } catch (RuntimeException exception) {
            LOGGER.warn("Run output activity Redis read unavailable, runId={}", runId.value(), exception);
            return true;
        }
    }

    /**
     * 记录当前 Run 最新状态停在 ask 类事件，等待用户处理期间不能被 stale 扫描标记为超时。
     */
    void markPendingAsk(RunId runId, RunEventType type, Instant occurredAt) {
        if (redisTemplate == null || runId == null) {
            return;
        }
        try {
            String value = "%s|%s".formatted(
                    type == null ? "unknown" : type.wireName(),
                    Objects.requireNonNullElseGet(occurredAt, Instant::now));
            redisTemplate.opsForValue().set(pendingAskKey(runId), value);
        } catch (RuntimeException exception) {
            LOGGER.debug("Run pending ask Redis write unavailable, runId={}", runId.value(), exception);
        }
    }

    /**
     * 清除已处理的 ask 状态；terminal Run 也会清理该状态，避免后续误判。
     */
    void clearPendingAsk(RunId runId) {
        if (redisTemplate == null || runId == null) {
            return;
        }
        try {
            redisTemplate.delete(pendingAskKey(runId));
        } catch (RuntimeException exception) {
            LOGGER.debug("Run pending ask Redis delete unavailable, runId={}", runId.value(), exception);
        }
    }

    /**
     * 判断当前 Run 是否仍等待用户处理 ask。Redis 不可用时保守返回 true。
     */
    boolean hasPendingAsk(RunId runId) {
        if (redisTemplate == null || runId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(pendingAskKey(runId)));
        } catch (RuntimeException exception) {
            LOGGER.warn("Run pending ask Redis read unavailable, runId={}", runId.value(), exception);
            return true;
        }
    }

    /**
     * Run 进入终态后清理收敛判断临时状态。
     */
    void clearRunState(RunId runId) {
        if (redisTemplate == null || runId == null) {
            return;
        }
        try {
            redisTemplate.delete(outputActivityKey(runId));
            redisTemplate.delete(pendingAskKey(runId));
        } catch (RuntimeException exception) {
            LOGGER.debug("Run activity Redis cleanup unavailable, runId={}", runId.value(), exception);
        }
    }

    private String outputActivityKey(RunId runId) {
        return OUTPUT_ACTIVITY_KEY_PREFIX + runId.value();
    }

    private String pendingAskKey(RunId runId) {
        return PENDING_ASK_KEY_PREFIX + runId.value();
    }
}
