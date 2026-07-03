package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RunSessionScopeRuntimeCacheTest {

    private static final RunId RUN_ID = new RunId("run_cache1234567890abcdef");
    private static final String SESSION_ID = "ses_child1234567890abcdef";
    private static final Duration TTL = Duration.ofMinutes(30);

    @Test
    void claimRawEventUsesRunScopeDedupKeyWithTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(
                "test-agent:run-scope:run_cache1234567890abcdef:dedup:ses_child1234567890abcdef:evt_raw_1",
                "1",
                TTL)).thenReturn(false);

        RunSessionScopeRuntimeCache cache = new RunSessionScopeRuntimeCache(redisTemplate, objectMapper());

        assertThat(cache.claimRawEvent(RUN_ID, SESSION_ID, "evt_raw_1")).isFalse();
    }

    @Test
    void pendingEventsAreSerializedAndDrainedInOriginalOrder() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ListOperations<String, String> lists = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(lists);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        String key = "test-agent:run-scope:run_cache1234567890abcdef:pending:ses_child1234567890abcdef";

        RunSessionScopeRuntimeCache cache = new RunSessionScopeRuntimeCache(redisTemplate, objectMapper());
        RunEventDraft draft = new RunEventDraft(
                RUN_ID,
                RunEventType.SESSION_STATUS,
                "trace_cache1234567890abcdef",
                Instant.parse("2026-07-03T02:30:00Z"),
                Map.of("rawType", "session.status", "sessionID", SESSION_ID));

        assertThat(cache.appendPending(SESSION_ID, draft)).isTrue();
        verify(lists).rightPush(eq(key), payload.capture());
        verify(redisTemplate).expire(key, TTL);
        when(lists.range(key, 0, -1)).thenReturn(List.of(payload.getValue()));

        List<RunEventDraft> drained = cache.drainPending(RUN_ID, SESSION_ID);

        assertThat(drained).singleElement().satisfies(restored -> {
            assertThat(restored.runId()).isEqualTo(RUN_ID);
            assertThat(restored.type()).isEqualTo(RunEventType.SESSION_STATUS);
            assertThat(restored.payload()).containsEntry("sessionID", SESSION_ID);
        });
        verify(redisTemplate).delete(key);
    }

    @Test
    void redisFailureDegradesWithoutBlockingRun() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        when(redisTemplate.opsForList()).thenThrow(new RedisConnectionFailureException("down"));
        RunSessionScopeRuntimeCache cache = new RunSessionScopeRuntimeCache(redisTemplate, objectMapper());

        assertThat(cache.claimRawEvent(RUN_ID, SESSION_ID, "evt_raw_1")).isTrue();
        assertThat(cache.appendPending(SESSION_ID, new RunEventDraft(
                RUN_ID,
                RunEventType.SESSION_STATUS,
                "trace_cache1234567890abcdef",
                Instant.now(),
                Map.of("sessionID", SESSION_ID)))).isFalse();
        assertThat(cache.drainPending(RUN_ID, SESSION_ID)).isEmpty();
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
