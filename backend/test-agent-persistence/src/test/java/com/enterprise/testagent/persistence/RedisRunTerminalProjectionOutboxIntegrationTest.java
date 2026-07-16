package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRuntimeInput;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 真实 Redis 验证终态事件与投影 outbox 的原子发布、版本确认和恢复索引自愈。 */
class RedisRunTerminalProjectionOutboxIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");
    private static final RunId RUN_ID = new RunId("run_terminal_outbox");

    @Test
    void terminalOutboxSurvivesCrashWindowUntilMatchingProjectionIsAcknowledged() {
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
        RedisRunRuntimeStore store = new RedisRunRuntimeStore(
                redis,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
                Duration.ofMinutes(30),
                100,
                1024 * 1024,
                100);
        try {
            RunRuntimeManifest manifest = manifest();
            store.initialize(manifest, new RunRuntimeInput(
                    RUN_ID, "prompt", List.of(), "msg_terminal_outbox", NOW.minusSeconds(10)));

            store.appendDurable(new RunEventDraft(
                    RUN_ID,
                    RunEventType.RUN_FAILED,
                    "trace_terminal_outbox",
                    NOW,
                    Map.of(
                            "status", RunStatus.FAILED.name(),
                            "terminalSource", "REMOTE_ROOT",
                            "terminalReasonCode", "REMOTE_FAILED",
                            "safeErrorMessage", "safe failure")));

            assertThat(store.findActiveByServer("server-a")).isEmpty();
            assertThat(store.findTerminalProjectionPendingByServer("server-a", 200))
                    .singleElement()
                    .satisfies(pending -> {
                        assertThat(pending.runId()).isEqualTo(RUN_ID);
                        assertThat(pending.version()).isEqualTo(1L);
                        assertThat(pending.status()).isEqualTo(RunStatus.FAILED);
                        assertThat(pending.terminalSource()).isEqualTo("REMOTE_ROOT");
                        assertThat(pending.safeErrorMessage()).isEqualTo("safe failure");
                    });
            assertThat(store.ackTerminalProjection(RUN_ID, 2L)).isFalse();
            assertThat(store.findTerminalProjectionPending(RUN_ID)).isPresent();

            assertThat(store.ackTerminalProjection(RUN_ID, 1L)).isTrue();
            assertThat(store.findTerminalProjectionPending(RUN_ID)).isEmpty();
            assertThat(store.findTerminalProjectionPendingByServer("server-a", 200)).isEmpty();

            String serverIndex = "test-agent:run:active:server:{server-a}";
            redis.opsForZSet().add(serverIndex, "run_dirty_terminal_outbox", NOW.plusSeconds(60).toEpochMilli());
            assertThat(store.findTerminalProjectionPendingByServer("server-a", 200)).isEmpty();
            assertThat(redis.opsForZSet().score(serverIndex, "run_dirty_terminal_outbox")).isNull();
        } finally {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }

    private RunRuntimeManifest manifest() {
        return new RunRuntimeManifest(
                RUN_ID,
                RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_terminal_outbox"),
                new SessionId("ses_terminal_outbox"),
                new WorkspaceId("wrk_terminal_outbox"),
                "opencode",
                "req_terminal_outbox",
                "msg_terminal_outbox",
                "server-a",
                "backend-a",
                "node-a",
                "process-a",
                "remote-a",
                RunStatus.RUNNING,
                1L,
                0L,
                1L,
                0L,
                false,
                0L,
                0L,
                null,
                null,
                null,
                NOW.plusSeconds(300),
                NOW.minusSeconds(30),
                NOW.minusSeconds(10));
    }
}
