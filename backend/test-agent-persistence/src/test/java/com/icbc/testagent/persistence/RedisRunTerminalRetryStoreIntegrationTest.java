package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icbc.testagent.domain.run.RunDiffCounts;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.RunTerminalProjection;
import com.icbc.testagent.domain.run.RunTerminalRetry;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

/** 使用真实 Redis 验证终态安全投影的到期索引、覆盖更新和 24 小时 TTL。 */
class RedisRunTerminalRetryStoreIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-11T02:00:00Z");
    private static final RunId RUN_ID = new RunId("run_terminal_retry_redis");

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void saveUsesOneAtomicSameSlotScript() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any())).thenReturn(1L);
        RedisRunTerminalRetryStore store = store(redis);

        store.save(RunTerminalRetry.pending(projection(3L), NOW, 7L));

        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ttl = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nextAttemptAt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> member = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(
                script.capture(),
                keys.capture(),
                encoded.capture(),
                ttl.capture(),
                nextAttemptAt.capture(),
                member.capture());
        assertThat(keys.getValue()).containsExactly(
                "test-agent:run-terminal-retry:{terminal-retry}:record:" + RUN_ID.value(),
                "test-agent:run-terminal-retry:{terminal-retry}:due");
        assertThat(script.getValue().getScriptAsString())
                .contains(
                        "cjson.decode",
                        "terminalProjectionVersion",
                        "lastEventSeq",
                        "failedAttempts",
                        "ARGV[1] ~= currentJson");
        assertThat(encoded.getValue())
                .contains("[REDACTED]")
                .contains("\"terminalProjectionVersion\":7");
        assertThat(ttl.getValue()).isEqualTo(Long.toString(Duration.ofHours(24).toMillis()));
        assertThat(nextAttemptAt.getValue())
                .isEqualTo(Long.toString(NOW.plusSeconds(5).toEpochMilli()));
        assertThat(member.getValue()).isEqualTo(RUN_ID.value());
        verify(redis, never()).opsForValue();
        verify(redis, never()).opsForZSet();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void deleteUsesOneAtomicSameSlotScript() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        RedisRunTerminalRetryStore store = store(redis);
        RunTerminalRetry retry = RunTerminalRetry.pending(projection(3L), NOW, 7L);

        assertThat(store.delete(retry)).isTrue();

        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> expected = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> member = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(script.capture(), keys.capture(), expected.capture(), member.capture());
        assertThat(keys.getValue()).containsExactly(
                "test-agent:run-terminal-retry:{terminal-retry}:record:" + RUN_ID.value(),
                "test-agent:run-terminal-retry:{terminal-retry}:due");
        assertThat(script.getValue().getScriptAsString())
                .contains("redis.call('GET'", "current ~= ARGV[1]", "ZREM", "DEL");
        assertThat(expected.getValue()).contains("\"terminalProjectionVersion\":7");
        assertThat(member.getValue()).isEqualTo(RUN_ID.value());
        verify(redis, never()).opsForZSet();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void danglingDueCleanupNeverDeletesAConcurrentOrMalformedRecord() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> due = mock(ZSetOperations.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForZSet()).thenReturn(due);
        when(due.rangeByScore(any(), any(Double.class), any(Double.class), any(Long.class), any(Long.class)))
                .thenReturn(new LinkedHashSet<>(List.of("broken-member", RUN_ID.value())));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("test-agent:run-terminal-retry:{terminal-retry}:record:" + RUN_ID.value()))
                .thenReturn(null);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        RedisRunTerminalRetryStore store = store(redis);

        assertThat(store.findDue(NOW, 10)).isEmpty();

        ArgumentCaptor<RedisScript> scripts = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> members = ArgumentCaptor.forClass(String.class);
        verify(redis, times(2)).execute(scripts.capture(), keys.capture(), members.capture());
        assertThat(scripts.getAllValues()).allSatisfy(script -> assertThat(script.getScriptAsString())
                .contains("ZREM")
                .doesNotContain("DEL"));
        assertThat(scripts.getAllValues()).anySatisfy(script -> assertThat(script.getScriptAsString())
                .contains("redis.call('GET'"));
        assertThat(scripts.getAllValues()).anySatisfy(script -> assertThat(script.getScriptAsString())
                .doesNotContain("redis.call('GET'"));
        assertThat(keys.getAllValues()).allSatisfy(actual -> assertThat(actual)
                .contains("test-agent:run-terminal-retry:{terminal-retry}:due"));
        assertThat(Set.copyOf(members.getAllValues()))
                .containsExactlyInAnyOrder("broken-member", RUN_ID.value());
    }

    @Test
    void staleGenerationCannotOverwriteOrDeleteNewerRetry() {
        String configuredPort = System.getProperty("test.redis.port");
        Assumptions.assumeTrue(configuredPort != null && !configuredPort.isBlank());
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                "127.0.0.1", Integer.parseInt(configuredPort));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        RedisRunTerminalRetryStore store = store(redis);
        RunTerminalRetry stale = RunTerminalRetry.pending(projection(3L), NOW, 7L);
        RunTerminalRetry current = RunTerminalRetry.pending(projection(4L), NOW, 8L);
        try {
            store.save(current);

            store.save(stale);
            assertThat(store.find(RUN_ID)).contains(current);
            assertThat(store.delete(stale)).isFalse();
            assertThat(store.find(RUN_ID)).contains(current);

            assertThat(store.delete(current)).isTrue();
            assertThat(store.find(RUN_ID)).isEmpty();
        } finally {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }

    @Test
    void olderRescheduleCannotMoveSameGenerationBackward() {
        String configuredPort = System.getProperty("test.redis.port");
        Assumptions.assumeTrue(configuredPort != null && !configuredPort.isBlank());
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                "127.0.0.1", Integer.parseInt(configuredPort));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        RedisRunTerminalRetryStore store = store(redis);
        RunTerminalRetry original = RunTerminalRetry.pending(projection(3L), NOW, 7L);
        RunTerminalRetry rescheduled = original.rescheduleAfterFailure(NOW.plusSeconds(5)).orElseThrow();
        try {
            store.save(rescheduled);

            store.save(original);
            assertThat(store.find(RUN_ID)).contains(rescheduled);
            assertThat(store.findDue(NOW.plusSeconds(5), 10)).isEmpty();
            assertThat(store.findDue(NOW.plusSeconds(20), 10)).containsExactly(rescheduled);
        } finally {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }

    @Test
    void storesOnlySafeProjectionAndReturnsItAtDueTime() {
        String configuredPort = System.getProperty("test.redis.port");
        Assumptions.assumeTrue(configuredPort != null && !configuredPort.isBlank());
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                "127.0.0.1", Integer.parseInt(configuredPort));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        RedisRunTerminalRetryStore store = store(redis);
        RunTerminalRetry retry = RunTerminalRetry.pending(projection(3L), NOW, 7L);
        try {
            store.save(retry);

            assertThat(store.find(RUN_ID)).contains(retry);
            assertThat(store.findDue(NOW.plusSeconds(4), 10)).isEmpty();
            assertThat(store.findDue(NOW.plusSeconds(5), 10)).containsExactly(retry);
            String recordKey = "test-agent:run-terminal-retry:{terminal-retry}:record:" + RUN_ID.value();
            String dueKey = "test-agent:run-terminal-retry:{terminal-retry}:due";
            assertThat(redis.opsForValue().get(recordKey))
                    .contains("[REDACTED]")
                    .doesNotContain("prompt", "partsJson", "raw-secret");
            assertThat(redis.opsForZSet().score(dueKey, RUN_ID.value()))
                    .isEqualTo((double) retry.nextAttemptAt().toEpochMilli());
            assertThat(redis.getExpire(recordKey))
                    .isPositive()
                    .isLessThanOrEqualTo(Duration.ofHours(24).toSeconds());

            RunTerminalRetry rescheduled = retry.rescheduleAfterFailure(NOW.plusSeconds(5)).orElseThrow();
            store.save(rescheduled);
            assertThat(store.find(RUN_ID)).contains(rescheduled);
            assertThat(store.findDue(NOW.plusSeconds(19), 10)).isEmpty();
            assertThat(store.findDue(NOW.plusSeconds(20), 10)).containsExactly(rescheduled);

            assertThat(store.delete(rescheduled)).isTrue();
            assertThat(store.find(RUN_ID)).isEmpty();
            assertThat(store.findDue(NOW.plusSeconds(20), 10)).isEmpty();
        } finally {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }

    private RedisRunTerminalRetryStore store(StringRedisTemplate redis) {
        return new RedisRunTerminalRetryStore(
                redis,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RunTerminalProjection projection(long lastEventSeq) {
        return new RunTerminalProjection(
                RUN_ID,
                new SessionId("ses_terminal_retry_redis"),
                RunStatus.SUCCEEDED,
                1,
                "REMOTE_ROOT",
                "COMPLETED",
                "[REDACTED]",
                false,
                lastEventSeq,
                NOW.plus(Duration.ofHours(24)),
                "remote-root",
                RunDiffCounts.empty(),
                null,
                null,
                TokenUsage.empty(),
                BigDecimal.ZERO,
                "trace_terminal_retry_redis",
                NOW,
                "opencode",
                ConversationSourceType.MANUAL,
                null,
                new UserId("usr_terminal_retry_redis"),
                List.of());
    }
}
