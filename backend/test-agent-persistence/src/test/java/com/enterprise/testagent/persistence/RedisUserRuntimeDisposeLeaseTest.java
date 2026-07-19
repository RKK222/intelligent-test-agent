package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.user.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** 验证用户级 dispose 闸门按 token 申请、续租和释放，防止过期调用误删新 owner。 */
class RedisUserRuntimeDisposeLeaseTest {

    private static final UserId USER_ID = new UserId("usr_dispose_lease");
    private static final String TOKEN = "dispose-token";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final Instant NOW = Instant.parse("2026-07-19T06:00:00Z");
    private static final String LOCK_KEY = "test-agent:run:runtime-dispose:{usr_dispose_lease}";

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void acquiresRenewsAndReleasesWithTheSameOwnerToken() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisRunRuntimeStore store = new RedisRunRuntimeStore(
                redis,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(redis.execute(
                argThat(script -> contains(script, "ZREMRANGEBYSCORE") && contains(script, "ZCARD")),
                eq(List.of(
                        "test-agent:run:active:user:{usr_dispose_lease}",
                        LOCK_KEY)),
                eq(TOKEN),
                eq(Long.toString(TTL.toMillis())),
                eq(Long.toString(NOW.toEpochMilli())))).thenReturn(1L);
        when(redis.execute(
                argThat(script -> contains(script, "PEXPIRE")),
                eq(List.of(LOCK_KEY)),
                eq(TOKEN),
                eq(Long.toString(TTL.toMillis())))).thenReturn(1L);

        assertThat(store.tryAcquireUserRuntimeDispose(USER_ID, TOKEN, TTL)).isTrue();
        assertThat(store.renewUserRuntimeDispose(USER_ID, TOKEN, TTL)).isTrue();
        store.releaseUserRuntimeDispose(USER_ID, TOKEN);

        verify(redis).execute(
                argThat(script -> contains(script, "return redis.call('DEL', KEYS[1])")),
                eq(List.of(LOCK_KEY)),
                eq(TOKEN));
    }

    private boolean contains(RedisScript<?> script, String fragment) {
        return script != null && script.getScriptAsString().contains(fragment);
    }
}
