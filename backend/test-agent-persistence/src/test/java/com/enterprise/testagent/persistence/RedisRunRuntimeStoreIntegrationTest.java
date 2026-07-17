package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.event.RunSessionScope;
import com.enterprise.testagent.domain.event.RunSessionScopeSession;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunDiffCounts;
import com.enterprise.testagent.domain.run.RunRuntimeAppendResult;
import com.enterprise.testagent.domain.run.RunRuntimeInput;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunRuntimeSnapshot;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 真实 Redis/Lua/Streams 验证；通过 {@code -Dtest.redis.port=<port>} 显式启用。 */
class RedisRunRuntimeStoreIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-10T08:00:00Z");

    @Test
    void atomicallyAppendsConcurrentEventsMaintainsIndexesAndResetsOnCapacity() {
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
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RedisRunRuntimeStore store = new RedisRunRuntimeStore(
                redis,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
                Duration.ofMinutes(30),
                100,
                1024 * 1024,
                64);
        RunRuntimeManifest manifest = manifest("run_redis_concurrent", RunStatus.RUNNING);
        try {
            store.initialize(manifest, input(manifest.runId()));
            assertThat(store.findInput(manifest.runId())).contains(input(manifest.runId()));
            assertThat(store.claimClientRequest(manifest.sessionId(), "req-1", manifest.runId())).isTrue();
            assertThat(store.claimClientRequest(
                    manifest.sessionId(), "req-1", new RunId("run_redis_duplicate"))).isFalse();
            assertThat(store.findByClientRequest(manifest.sessionId(), "req-1")).contains(manifest.runId());
            store.releaseClientRequest(manifest.sessionId(), "req-1", new RunId("run_redis_duplicate"));
            assertThat(store.findByClientRequest(manifest.sessionId(), "req-1")).contains(manifest.runId());
            store.bindRemoteSession(manifest.runId(), "remote-session-bound");
            assertThat(store.findManifest(manifest.runId()).orElseThrow().rootRemoteSessionId())
                    .isEqualTo("remote-session-bound");
            List<CompletableFuture<RunRuntimeAppendResult>> futures = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                int eventIndex = index;
                futures.add(CompletableFuture.supplyAsync(() -> store.appendDurable(
                        draft(manifest.runId(), eventIndex))));
            }
            List<Long> seqs = futures.stream().map(CompletableFuture::join)
                    .map(result -> result.event().seq()).sorted().toList();

            assertThat(seqs).containsExactlyElementsOf(
                    java.util.stream.LongStream.rangeClosed(1, 24).boxed().toList());
            assertThat(store.replayAfter(manifest.runId(), 0, 100).durableEvents())
                    .extracting(event -> event.seq())
                    .containsExactlyElementsOf(seqs);
            assertThat(store.findActiveBySession(manifest.sessionId()))
                    .map(RunRuntimeManifest::runId)
                    .contains(manifest.runId());
            assertThat(store.findActiveByUser(manifest.userId())).extracting(item -> item.runId())
                    .containsExactly(manifest.runId());
            assertThat(store.findActiveByServer(manifest.producerLinuxServerId())).extracting(item -> item.runId())
                    .containsExactly(manifest.runId());
            assertThat(redis.keys("test-agent:run:{" + manifest.runId().value() + "}:*"))
                    .isNotEmpty()
                    .allSatisfy(key -> assertThat(key).contains("{" + manifest.runId().value() + "}"));
            assertThat(redis.getExpire("test-agent:run:{" + manifest.runId().value() + "}:manifest"))
                    .isPositive();
            store.appendDurable(new RunEventDraft(
                    manifest.runId(), RunEventType.DIFF_PROPOSED, "trace_diff", NOW, Map.of("diffId", "diff-1")));
            store.appendDurable(new RunEventDraft(
                    manifest.runId(), RunEventType.DIFF_ACCEPTED, "trace_diff", NOW, Map.of("diffId", "diff-1")));
            assertThat(store.diffCounts(manifest.runId())).isEqualTo(new RunDiffCounts(1, 1, 0));

            RunRuntimeManifest current = store.findManifest(manifest.runId()).orElseThrow();
            store.updateStatus(manifest.runId(), RunStatus.SUCCEEDED, current.statusVersion(), null);
            long runtimeVersionBeforeLateStarted = store.replayAfter(manifest.runId(), 0, 100)
                    .snapshot().runtimeVersion();
            assertThat(store.findActiveBySession(manifest.sessionId())).isEmpty();
            assertThat(store.findActiveByUser(manifest.userId())).isEmpty();
            assertThat(store.findRecentBySession(manifest.sessionId(), 10))
                    .extracting(RunRuntimeManifest::runId)
                    .containsExactly(manifest.runId());
            RunRuntimeAppendResult ignored = store.appendDurable(new RunEventDraft(
                    manifest.runId(), RunEventType.RUN_STARTED, "trace_late_started", NOW.plusSeconds(1),
                    Map.of("status", "RUNNING")));
            assertThat(ignored.visible()).isFalse();
            assertThat(store.projectTransient(new RunEventDraft(
                    manifest.runId(), RunEventType.RUN_STARTED, "trace_late_started_transient", NOW.plusSeconds(2),
                    Map.of("status", "RUNNING")))).isFalse();
            RunRuntimeManifest afterLateStarted = store.findManifest(manifest.runId()).orElseThrow();
            assertThat(afterLateStarted.status()).isEqualTo(RunStatus.SUCCEEDED);
            assertThat(store.replayAfter(manifest.runId(), 0, 100).snapshot().runtimeVersion())
                    .isEqualTo(runtimeVersionBeforeLateStarted);
            assertThat(store.replayAfter(manifest.runId(), 0, 100).snapshot().events())
                    .noneMatch(event -> event.type() == RunEventType.RUN_STARTED);
            assertThat(store.tailAfter(manifest.runId(), runtimeVersionBeforeLateStarted, 100).events()).isEmpty();
            assertThat(store.findActiveBySession(manifest.sessionId())).isEmpty();
            assertThatThrownBy(() -> store.updateStatus(
                            manifest.runId(), RunStatus.RUNNING, afterLateStarted.statusVersion(), null))
                    .isInstanceOfSatisfying(PlatformException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        } finally {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }

    @Test
    void capacityOverflowDeletesStreamAndRequiresSnapshotReset() {
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
                3,
                1024 * 1024,
                16);
        RunRuntimeManifest manifest = manifest("run_redis_capacity", RunStatus.RUNNING);
        try {
            store.initialize(manifest, input(manifest.runId()));
            RunRuntimeAppendResult overflow = null;
            for (int index = 0; index < 4; index++) {
                overflow = store.appendDurable(draft(manifest.runId(), index));
            }

            assertThat(overflow).isNotNull();
            assertThat(overflow.truncatedNow()).isTrue();
            assertThat(overflow.resetGeneration()).isEqualTo(1L);
            var replay = store.replayAfter(manifest.runId(), 0, 100);
            assertThat(replay.cursorResetRequired()).isTrue();
            assertThat(replay.snapshot().barrierSeq()).isEqualTo(4L);
            // 同一 message/part 的多次 full update 被物化为最终可见状态，而不是保留四条尾日志。
            assertThat(replay.snapshot().events())
                    .filteredOn(event -> event.type() == RunEventType.MESSAGE_PART_UPDATED)
                    .singleElement()
                    .satisfies(event -> assertThat(event.payload()).containsEntry("text", "delta-3"));
            assertThat(replay.durableEvents()).isEmpty();
            assertThat(replay.manifest().detailsTruncated()).isTrue();
            assertThat(replay.manifest().earliestSeq()).isEqualTo(5L);
            var tail = store.tailAfter(manifest.runId(), 0, 100);
            assertThat(tail.resetRequired()).isTrue();
            assertThat(tail.snapshot().events())
                    .filteredOn(event -> event.type() == RunEventType.MESSAGE_PART_UPDATED)
                    .hasSize(1);
            RunRuntimeAppendResult afterReset = store.appendDurable(draft(manifest.runId(), 4));
            assertThat(afterReset.truncatedNow()).isFalse();
        } finally {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }

    @Test
    void capacityResetRetainsUserInputLatestAssistantPartAndRunStatus() {
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
                4);
        RunRuntimeManifest manifest = manifest("run_redis_critical_capacity", RunStatus.RUNNING);
        try {
            store.initialize(manifest, input(manifest.runId()));
            store.appendDurable(message(manifest.runId(), "msg_old", "old answer", 1));
            store.appendDurable(message(manifest.runId(), "msg_final", "final answer", 2));
            store.appendDurable(new RunEventDraft(
                    manifest.runId(), RunEventType.MESSAGE_PART_UPDATED, "trace_capacity", NOW.plusMillis(3),
                    Map.of("part", Map.of(
                            "id", "part_final",
                            "messageID", "msg_final",
                            "sessionID", manifest.rootRemoteSessionId(),
                            "type", "text",
                            "text", "final visible part"))));
            store.appendDurable(new RunEventDraft(
                    manifest.runId(), RunEventType.MESSAGE_PART_UPDATED, "trace_capacity", NOW.plusMillis(4),
                    Map.of("part", Map.of(
                            "id", "part_reasoning",
                            "messageID", "msg_final",
                            "sessionID", manifest.rootRemoteSessionId(),
                            "type", "reasoning",
                            "text", "internal reasoning must not replace visible part"))));
            store.appendDurable(message(manifest.runId(), "msg_echo", "later non-assistant message", 5, "user"));
            RunRuntimeAppendResult terminal = store.appendDurable(new RunEventDraft(
                    manifest.runId(), RunEventType.RUN_SUCCEEDED, "trace_capacity", NOW.plusMillis(6),
                    Map.of("status", RunStatus.SUCCEEDED.name())));

            assertThat(terminal.truncatedNow()).isTrue();
            var replay = store.replayAfter(manifest.runId(), 0, 100);
            assertThat(replay.cursorResetRequired()).isTrue();
            assertThat(replay.snapshot().events())
                    .anySatisfy(event -> assertThat(event.payload())
                            .containsEntry("role", "user")
                            .containsEntry("text", "完整 prompt 不得进数据库"))
                    .anySatisfy(event -> assertThat(event.payload().get("message"))
                            .isEqualTo(Map.of(
                                    "id", "msg_final",
                                    "role", "assistant",
                                    "sessionID", manifest.rootRemoteSessionId(),
                                    "text", "final answer")))
                    .anySatisfy(event -> assertThat(event.payload().get("part"))
                            .isEqualTo(Map.of(
                                    "id", "part_final",
                                    "messageID", "msg_final",
                                    "sessionID", manifest.rootRemoteSessionId(),
                                    "type", "text",
                                    "text", "final visible part")))
                    .anySatisfy(event -> assertThat(event.type()).isEqualTo(RunEventType.RUN_SUCCEEDED));
            assertThat(replay.snapshot().events())
                    .noneMatch(event -> "msg_old".equals(String.valueOf(
                            ((Map<?, ?>) event.payload().getOrDefault("message", Map.of())).get("id"))));
            assertThat(replay.snapshot().events())
                    .noneMatch(event -> "msg_echo".equals(String.valueOf(
                            ((Map<?, ?>) event.payload().getOrDefault("message", Map.of())).get("id")))
                            || "part_reasoning".equals(String.valueOf(
                            ((Map<?, ?>) event.payload().getOrDefault("part", Map.of())).get("id"))));
            assertThat(replay.manifest().detailBytes()).isLessThanOrEqualTo(1024 * 1024L);
        } finally {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }

    @Test
    void normalizesOversizedSnapshotAndDoesNotEnterPerEventResetLoop() {
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
                2048,
                100);
        RunRuntimeManifest manifest = manifest("run_redis_byte_capacity", RunStatus.RUNNING);
        try {
            store.initialize(manifest, input(manifest.runId()));
            RunRuntimeAppendResult overflow = store.appendDurable(new RunEventDraft(
                    manifest.runId(), RunEventType.MESSAGE_PART_UPDATED, "trace_large", NOW,
                    Map.of("messageId", "msg_large", "partId", "part_large", "text", "x".repeat(10_000))));
            assertThat(overflow.truncatedNow()).isTrue();
            assertThat(store.findManifest(manifest.runId()).orElseThrow().detailBytes()).isLessThanOrEqualTo(2048L);

            RunRuntimeAppendResult next = store.appendDurable(new RunEventDraft(
                    manifest.runId(), RunEventType.MESSAGE_PART_UPDATED, "trace_small", NOW.plusSeconds(1),
                    Map.of("messageId", "msg_large", "partId", "part_large", "text", "done")));
            assertThat(next.truncatedNow()).isFalse();
        } finally {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }

    @Test
    void materializesTransientDeltasAndReadsRuntimeStreamInVersionOrder() {
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
        RunRuntimeManifest manifest = manifest("run_redis_materialized", RunStatus.RUNNING);
        try {
            store.initialize(manifest, input(manifest.runId()));
            assertThat(store.replayAfter(manifest.runId(), 0, 100).snapshot().events())
                    .filteredOn(event -> event.type() == RunEventType.MESSAGE_UPDATED)
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.payload()).containsEntry("messageId", "msg_input");
                        assertThat(event.payload()).containsEntry("role", "user");
                        assertThat(event.payload()).containsEntry("text", "完整 prompt 不得进数据库");
                    });
            store.projectTransient(delta(manifest.runId(), "hel", 1));
            store.projectTransient(delta(manifest.runId(), "lo", 2));

            var replay = store.replayAfter(manifest.runId(), 0, 100);
            assertThat(replay.snapshot().runtimeVersion()).isEqualTo(2L);
            assertThat(replay.snapshot().events())
                    .filteredOn(event -> event.type() == RunEventType.MESSAGE_PART_DELTA)
                    .singleElement().satisfies(event -> {
                assertThat(event.type()).isEqualTo(RunEventType.MESSAGE_PART_DELTA);
                assertThat(event.payload()).containsEntry("delta", "hello");
            });

            store.appendDurable(new RunEventDraft(
                    manifest.runId(), RunEventType.TODO_UPDATED, "trace_redis_runtime", NOW,
                    Map.of("sessionId", "remote-session-redis-runtime", "todos", List.of())));
            store.projectTransient(new RunEventDraft(
                    manifest.runId(), RunEventType.QUESTION_ASKED, "trace_redis_runtime", NOW,
                    Map.of("sessionId", "remote-session-redis-runtime", "requestId", "question_1")));

            var tail = store.tailAfter(manifest.runId(), replay.snapshot().runtimeVersion(), 100);
            assertThat(tail.resetRequired()).isFalse();
            assertThat(tail.events()).extracting(item -> item.runtimeVersion())
                    .containsExactly(3L, 4L);
            assertThat(tail.events()).extracting(item -> item.durable())
                    .containsExactly(true, false);
            RunRuntimeManifest current = store.findManifest(manifest.runId()).orElseThrow();
            assertThat(current.attention()).isEqualTo("QUESTION");
            assertThat(current.attentionEventId()).isEqualTo(
                    "evt_runtime_" + manifest.runId().value() + "_4");
            assertThat(current.attentionAt()).isEqualTo(NOW);
            assertThat(redis.getExpire("test-agent:run:runtime-user:{" + manifest.userId().value() + "}"))
                    .isGreaterThan(Duration.ofMinutes(10).toSeconds());
            String activeUserIndex = "test-agent:run:active:user:{" + manifest.userId().value() + "}";
            String activeServerIndex = "test-agent:run:active:server:{server-a}";
            String historyIndex = "test-agent:run:history:session:{" + manifest.sessionId().value() + "}";
            long pendingUserTtl = redis.getExpire(activeUserIndex);
            long pendingServerTtl = redis.getExpire(activeServerIndex);
            long pendingHistoryTtl = redis.getExpire(historyIndex);
            RunRuntimeManifest regular = manifest("run_redis_shared_regular", RunStatus.RUNNING);
            store.initialize(regular, input(regular.runId()));
            assertThat(redis.getExpire(activeUserIndex)).isGreaterThanOrEqualTo(pendingUserTtl - 2L);
            assertThat(redis.getExpire(activeServerIndex)).isGreaterThanOrEqualTo(pendingServerTtl - 2L);
            assertThat(redis.getExpire(historyIndex)).isGreaterThanOrEqualTo(pendingHistoryTtl - 2L);
            store.projectTransient(new RunEventDraft(
                    manifest.runId(), RunEventType.QUESTION_ASKED, "trace_redis_runtime", NOW.plusSeconds(1),
                    Map.of("sessionId", "remote-session-redis-runtime", "requestId", "question_2")));
            store.projectTransient(new RunEventDraft(
                    manifest.runId(), RunEventType.QUESTION_REPLIED, "trace_redis_runtime", NOW.plusSeconds(2),
                    Map.of("sessionId", "remote-session-redis-runtime", "requestId", "question_1")));
            RunRuntimeManifest afterOldReply = store.findManifest(manifest.runId()).orElseThrow();
            assertThat(afterOldReply.attention()).isEqualTo("QUESTION");
            assertThat(afterOldReply.attentionEventId()).isEqualTo(
                    "evt_runtime_" + manifest.runId().value() + "_5");
            store.appendDurable(new RunEventDraft(
                    manifest.runId(), RunEventType.RUN_CANCELLED, "trace_redis_runtime", NOW.plusSeconds(3),
                    Map.of("status", RunStatus.CANCELLED.name(), "reason", "PENDING_ASK_EXPIRED")));
            RunRuntimeManifest terminal = store.findManifest(manifest.runId()).orElseThrow();
            assertThat(terminal.status()).isEqualTo(RunStatus.CANCELLED);
            assertThat(terminal.attention()).isNull();
            assertThat(terminal.attentionEventId()).isNull();
            assertThat(terminal.attentionAt()).isNull();
            assertThat(redis.getExpire("test-agent:run:{" + manifest.runId().value() + "}:input"))
                    .isPositive();
            assertThat(redis.getExpire("test-agent:run:active:session:{" + manifest.sessionId().value() + "}"))
                    .isPositive();
        } finally {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }

    @Test
    void keepsScopeVersionConsistentAndDrainsPendingAtomically() {
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
                redis, new ObjectMapper().registerModule(new JavaTimeModule()), Clock.fixed(NOW, ZoneOffset.UTC));
        RunRuntimeManifest manifest = manifest("run_redis_scope", RunStatus.RUNNING);
        try {
            store.initialize(manifest, input(manifest.runId()));
            RunSessionScope scope = new RunSessionScope(
                    manifest.runId(), manifest.rootRemoteSessionId(), 1L, "trace_scope", NOW, NOW, Map.of());
            RunSessionScopeSession root = new RunSessionScopeSession(
                    manifest.runId(), manifest.rootRemoteSessionId(), manifest.rootRemoteSessionId(), null,
                    false, "ROOT", null, null, null, "trace_scope", NOW, NOW, Map.of());
            store.saveScope(scope, root);
            assertThat(store.scopeVersion(manifest.runId())).isEqualTo(1L);
            assertThat(store.findScopeSession(manifest.runId(), manifest.rootRemoteSessionId())).contains(root);
            assertThat(store.claimRawEvent(manifest.runId(), manifest.rootRemoteSessionId(), "raw_1")).isTrue();
            assertThat(store.claimRawEvent(manifest.runId(), manifest.rootRemoteSessionId(), "raw_1")).isFalse();

            RunEventDraft pending = delta(manifest.runId(), "pending", 1);
            long detailBytesBeforePending = store.findManifest(manifest.runId()).orElseThrow().detailBytes();
            RedisRunRuntimeStore laterStore = new RedisRunRuntimeStore(
                    redis,
                    new ObjectMapper().registerModule(new JavaTimeModule()),
                    Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC));
            laterStore.appendPending("child_1", pending);
            RunRuntimeManifest withPending = store.findManifest(manifest.runId()).orElseThrow();
            assertThat(withPending.detailBytes()).isGreaterThan(detailBytesBeforePending);
            assertThat(withPending.updatedAt()).isEqualTo(NOW.plusSeconds(30));
            assertThat(store.drainPending(manifest.runId(), "child_1")).containsExactly(pending);
            assertThat(store.findManifest(manifest.runId()).orElseThrow().detailBytes())
                    .isEqualTo(detailBytesBeforePending);
            assertThat(store.drainPending(manifest.runId(), "child_1")).isEmpty();

            var config = redis.getConnectionFactory().getConnection().serverCommands();
            assertThat(config.getConfig("maxmemory-policy").getProperty("maxmemory-policy"))
                    .isEqualTo("noeviction");
            assertThat(config.getConfig("appendfsync").getProperty("appendfsync"))
                    .isEqualTo("everysec");

            RedisRunRuntimeStore capacityLimitedStore = new RedisRunRuntimeStore(
                    redis,
                    new ObjectMapper().registerModule(new JavaTimeModule()),
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    Duration.ofMinutes(5),
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(30),
                    100,
                    2048,
                    100);
            RunSessionScopeSession oversized = new RunSessionScopeSession(
                    manifest.runId(), "child_oversized", manifest.rootRemoteSessionId(), manifest.rootRemoteSessionId(),
                    false, "CHILD", null, null, null, "trace_scope", NOW, NOW,
                    Map.of("payload", "x".repeat(4096)));
            assertThatThrownBy(() -> capacityLimitedStore.saveScope(scope, oversized))
                    .isInstanceOfSatisfying(PlatformException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
            RunEventDraft oversizedPending = delta(manifest.runId(), "x".repeat(4096), 2);
            assertThatThrownBy(() -> capacityLimitedStore.appendPending("child_oversized", oversizedPending))
                    .isInstanceOfSatisfying(PlatformException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        } finally {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }

    @Test
    void materializesRealOpencodeNestedMessageShapesWithoutEntityCollision() {
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
                redis, new ObjectMapper().registerModule(new JavaTimeModule()), Clock.fixed(NOW, ZoneOffset.UTC));
        RunRuntimeManifest manifest = manifest("run_redis_nested_shape", RunStatus.RUNNING);
        try {
            store.initialize(manifest, input(manifest.runId()));
            store.projectTransient(new RunEventDraft(
                    manifest.runId(), RunEventType.MESSAGE_UPDATED, "trace_nested", NOW,
                    Map.of("info", Map.of("id", "msg_1", "sessionID", manifest.rootRemoteSessionId()))));
            store.projectTransient(new RunEventDraft(
                    manifest.runId(), RunEventType.MESSAGE_UPDATED, "trace_nested", NOW,
                    Map.of("info", Map.of("id", "msg_2", "sessionID", manifest.rootRemoteSessionId()))));
            store.projectTransient(delta(manifest.runId(), "old-text", 1));
            store.projectTransient(new RunEventDraft(
                    manifest.runId(), RunEventType.MESSAGE_PART_DELTA, "trace_nested", NOW.plusMillis(2),
                    Map.of(
                            "sessionID", manifest.rootRemoteSessionId(),
                            "messageID", "msg_assistant",
                            "partID", "part_text",
                            "field", "reasoning",
                            "delta", "old-reasoning")));
            store.projectTransient(new RunEventDraft(
                    manifest.runId(), RunEventType.MESSAGE_PART_UPDATED, "trace_nested", NOW.plusMillis(3),
                    Map.of("part", Map.of(
                            "id", "part_text",
                            "messageID", "msg_assistant",
                            "sessionID", manifest.rootRemoteSessionId(),
                            "type", "text",
                            "text", "final-text"))));

            var snapshot = store.replayAfter(manifest.runId(), 0, 100).snapshot();
            assertThat(snapshot.events()).filteredOn(event -> event.type() == RunEventType.MESSAGE_UPDATED
                            && event.payload().containsKey("info"))
                    .extracting(event -> String.valueOf(((Map<?, ?>) event.payload().get("info")).get("id")))
                    .containsExactlyInAnyOrder("msg_1", "msg_2");
            assertThat(snapshot.events()).noneMatch(event -> event.type() == RunEventType.MESSAGE_PART_DELTA);
            assertThat(snapshot.events()).filteredOn(event -> event.type() == RunEventType.MESSAGE_PART_UPDATED)
                    .singleElement()
                    .satisfies(event -> assertThat(((Map<?, ?>) event.payload().get("part")).get("text"))
                            .isEqualTo("final-text"));
        } finally {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }

    @Test
    void rejectsExternalSnapshotWhenTransientRuntimeVersionMovedPastBarrier() {
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
                redis, new ObjectMapper().registerModule(new JavaTimeModule()), Clock.fixed(NOW, ZoneOffset.UTC));
        RunRuntimeManifest manifest = manifest("run_redis_snapshot_cas", RunStatus.RUNNING);
        try {
            store.initialize(manifest, input(manifest.runId()));
            RunEventDraft delta = delta(manifest.runId(), "newer", 1);
            store.projectTransient(delta);

            assertThatThrownBy(() -> store.saveSnapshot(new RunRuntimeSnapshot(
                            manifest.runId(), 0L, 0L, 0L, List.of(), NOW)))
                    .isInstanceOfSatisfying(PlatformException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

            store.saveSnapshot(new RunRuntimeSnapshot(
                    manifest.runId(), 0L, 1L, 0L, List.of(delta), NOW));
            assertThat(store.replayAfter(manifest.runId(), 0, 10).snapshot().events())
                    .containsExactly(delta);
        } finally {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }

    private RunRuntimeManifest manifest(String runId, RunStatus status) {
        return new RunRuntimeManifest(
                new RunId(runId),
                RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_redis_runtime"),
                new SessionId("ses_redis_runtime"),
                new WorkspaceId("wrk_redis_runtime"),
                "opencode",
                "req_redis_runtime",
                "msg_dispatch_redis_runtime",
                "server-a",
                "bjp_server_a",
                "node_ocp_redis_runtime",
                "ocp_redis_runtime",
                "remote-session-redis-runtime",
                status,
                0,
                0,
                1,
                0,
                false,
                0,
                0,
                null,
                null,
                null,
                NOW.plus(Duration.ofHours(3)),
                NOW,
                NOW);
    }

    private RunRuntimeInput input(RunId runId) {
        return new RunRuntimeInput(runId, "完整 prompt 不得进数据库", List.of(Map.of("type", "text")), "msg_input", NOW);
    }

    private RunEventDraft draft(RunId runId, int index) {
        return new RunEventDraft(
                runId,
                RunEventType.MESSAGE_PART_UPDATED,
                "trace_redis_runtime",
                NOW.plusMillis(index),
                Map.of("messageId", "msg_assistant", "partId", "part_text", "text", "delta-" + index));
    }

    private RunEventDraft message(RunId runId, String messageId, String text, int index) {
        return message(runId, messageId, text, index, "assistant");
    }

    private RunEventDraft message(RunId runId, String messageId, String text, int index, String role) {
        return new RunEventDraft(
                runId,
                RunEventType.MESSAGE_UPDATED,
                "trace_redis_runtime",
                NOW.plusMillis(index),
                Map.of("message", Map.of(
                        "id", messageId,
                        "role", role,
                        "sessionID", "remote-session-redis-runtime",
                        "text", text)));
    }

    private RunEventDraft delta(RunId runId, String delta, int index) {
        return new RunEventDraft(
                runId,
                RunEventType.MESSAGE_PART_DELTA,
                "trace_redis_runtime",
                NOW.plusMillis(index),
                Map.of(
                        "sessionId", "remote-session-redis-runtime",
                        "messageId", "msg_assistant",
                        "partId", "part_text",
                        "field", "text",
                        "delta", delta));
    }
}
