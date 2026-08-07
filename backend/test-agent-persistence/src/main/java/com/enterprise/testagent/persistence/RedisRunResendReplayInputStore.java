package com.enterprise.testagent.persistence;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunResendReplayInput;
import com.enterprise.testagent.domain.run.RunResendReplayInputStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 精确重放输入的 Redis 实现；key 与 JSON 均不进入日志。 */
public class RedisRunResendReplayInputStore implements RunResendReplayInputStore {

    private static final String KEY_PREFIX = "test-agent:run-resend-input:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RedisRunResendReplayInputStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, Clock.systemUTC());
    }

    RedisRunResendReplayInputStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Clock clock) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void save(RunResendReplayInput input) {
        Objects.requireNonNull(input, "input must not be null");
        Duration ttl = Duration.between(clock.instant(), input.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "重发输入已过期");
        }
        try {
            redisTemplate.opsForValue().set(
                    key(input.replacementRunId()), objectMapper.writeValueAsString(input), ttl);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<RunResendReplayInput> find(RunId replacementRunId) {
        try {
            String json = redisTemplate.opsForValue().get(key(replacementRunId));
            return json == null
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(json, RunResendReplayInput.class));
        } catch (JsonProcessingException | RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void delete(RunId replacementRunId) {
        try {
            redisTemplate.delete(key(replacementRunId));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private String key(RunId runId) {
        return KEY_PREFIX + runId.value();
    }

    private PlatformException unavailable(Exception exception) {
        if (exception instanceof PlatformException platformException) return platformException;
        return new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "重发输入运行态不可用");
    }
}
