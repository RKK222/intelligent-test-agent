package com.enterprise.testagent.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class RedisScheduledTaskLockTest {

    @Test
    void acquireUsesRedisSetNxAndLeaseUsesTokenScripts() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        ScheduledTaskKey taskKey = new ScheduledTaskKey("daily.cleanup");
        Duration ttl = Duration.ofMinutes(5);
        String lockKey = RedisScheduledTaskLock.lockKey(taskKey);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(lockKey), anyString(), eq(ttl))).thenReturn(true);
        RedisScheduledTaskLock lock = new RedisScheduledTaskLock(redisTemplate);

        ScheduledTaskLockLease lease = lock.acquire(taskKey, ttl).orElseThrow();

        assertThat(lease.lockKey()).isEqualTo("test-agent:scheduler:lock:daily.cleanup");
        assertThat(lease.token()).isNotBlank();
        when(redisTemplate.execute(
                        ArgumentMatchers.<DefaultRedisScript<Long>>any(),
                        eq(List.of(lockKey)),
                        eq(lease.token()),
                        eq(String.valueOf(ttl.toMillis()))))
                .thenReturn(1L);
        when(redisTemplate.execute(
                        ArgumentMatchers.<DefaultRedisScript<Long>>any(),
                        eq(List.of(lockKey)),
                        eq(lease.token())))
                .thenReturn(1L);
        assertThat(lease.renew()).isTrue();
        assertThat(lease.release()).isTrue();
    }

    @Test
    void acquireReturnsEmptyWhenRedisLockAlreadyExists() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        ScheduledTaskKey taskKey = new ScheduledTaskKey("daily.cleanup");
        Duration ttl = Duration.ofMinutes(5);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(RedisScheduledTaskLock.lockKey(taskKey)), anyString(), eq(ttl)))
                .thenReturn(false);

        assertThat(new RedisScheduledTaskLock(redisTemplate).acquire(taskKey, ttl)).isEmpty();
    }

    @Test
    void inspectReportsUnlockedWhenRedisKeyDoesNotExist() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ScheduledTaskKey taskKey = new ScheduledTaskKey("daily.cleanup");
        when(redisTemplate.getExpire(RedisScheduledTaskLock.lockKey(taskKey), TimeUnit.MILLISECONDS)).thenReturn(-2L);

        ScheduledTaskLockInspection inspection = new RedisScheduledTaskLock(redisTemplate).inspect(taskKey);

        assertThat(inspection.checkable()).isTrue();
        assertThat(inspection.locked()).isFalse();
        assertThat(inspection.ttlMillis()).isNull();
        assertThat(inspection.lockKey()).isEqualTo("test-agent:scheduler:lock:daily.cleanup");
    }

    @Test
    void inspectReportsHeldLockWithTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ScheduledTaskKey taskKey = new ScheduledTaskKey("daily.cleanup");
        when(redisTemplate.getExpire(RedisScheduledTaskLock.lockKey(taskKey), TimeUnit.MILLISECONDS)).thenReturn(42_000L);

        ScheduledTaskLockInspection inspection = new RedisScheduledTaskLock(redisTemplate).inspect(taskKey);

        assertThat(inspection.locked()).isTrue();
        assertThat(inspection.ttlMillis()).isEqualTo(42_000L);
    }

    @Test
    void inspectReportsHeldLockWithoutTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ScheduledTaskKey taskKey = new ScheduledTaskKey("daily.cleanup");
        when(redisTemplate.getExpire(RedisScheduledTaskLock.lockKey(taskKey), TimeUnit.MILLISECONDS)).thenReturn(-1L);

        ScheduledTaskLockInspection inspection = new RedisScheduledTaskLock(redisTemplate).inspect(taskKey);

        assertThat(inspection.locked()).isTrue();
        assertThat(inspection.ttlMillis()).isNull();
    }
}
