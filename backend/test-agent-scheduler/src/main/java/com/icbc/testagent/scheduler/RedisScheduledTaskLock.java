package com.icbc.testagent.scheduler;

import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis SET NX PX 分布式锁实现，释放和续租均通过 Lua 按 token 校验所有权。
 */
@Component
public class RedisScheduledTaskLock implements ScheduledTaskLock {

    private static final String KEY_PREFIX = "test-agent:scheduler:lock:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 注入 Spring Redis 字符串模板，锁值只保存随机 token。
     */
    public RedisScheduledTaskLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    @Override
    public Optional<ScheduledTaskLockLease> acquire(ScheduledTaskKey taskKey, Duration ttl) {
        Objects.requireNonNull(taskKey, "taskKey must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        String lockKey = lockKey(taskKey);
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }
        return Optional.of(new RedisScheduledTaskLockLease(taskKey, lockKey, token, ttl, redisTemplate));
    }

    /**
     * 构造稳定 Redis 锁 key。
     */
    public static String lockKey(ScheduledTaskKey taskKey) {
        return KEY_PREFIX + taskKey.value();
    }

    private record RedisScheduledTaskLockLease(
            ScheduledTaskKey taskKey,
            String lockKey,
            String token,
            Duration ttl,
            StringRedisTemplate redisTemplate) implements ScheduledTaskLockLease {

        @Override
        public boolean renew() {
            Long result = redisTemplate.execute(RENEW_SCRIPT, List.of(lockKey), token, String.valueOf(ttl.toMillis()));
            return result != null && result > 0;
        }

        @Override
        public boolean release() {
            Long result = redisTemplate.execute(RELEASE_SCRIPT, List.of(lockKey), token);
            return result != null && result > 0;
        }
    }
}
