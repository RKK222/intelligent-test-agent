package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

/** 验证跨 slot 索引先保守登记，避免 Run Lua 成功后 Java 崩溃造成运行态失联。 */
class RedisRunRuntimeIndexReservationTest {

    private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void clientRequestClaimUsesShortCrashWindowAndConfirmationExtendsIt() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
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
        SessionId sessionId = new SessionId("ses_client_request_window");
        RunId runId = new RunId("run_client_request_window");

        org.assertj.core.api.Assertions.assertThat(
                store.claimClientRequest(sessionId, "request-window", runId)).isTrue();
        org.assertj.core.api.Assertions.assertThat(
                store.confirmClientRequest(sessionId, "request-window", runId)).isTrue();

        verify(values).setIfAbsent(
                any(), eq(runId.value()), eq(Duration.ofSeconds(30)));
        verify(redis).execute(
                argThat(script -> script.getScriptAsString().contains("current ~= ARGV[1]")),
                anyList(),
                eq(runId.value()),
                eq(Long.toString(Duration.ofMinutes(30).toMillis())));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void reservesMaximumRetentionIndexesBeforeInitializingRunSlot() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ZSetOperations<String, String> zsets = mock(ZSetOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForZSet()).thenReturn(zsets);
        when(redis.execute(
                any(RedisScript.class),
                anyList(),
                any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(1L);
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
        RunRuntimeManifest manifest = manifest();
        Duration maximumRetention = Duration.ofMinutes(70);
        long maximumScore = NOW.plus(maximumRetention).toEpochMilli();
        String userIndex = "test-agent:run:active:user:{usr_index_reservation}";
        String sessionIndex = "test-agent:run:active:session:{ses_index_reservation}";
        String serverIndex = "test-agent:run:active:server:{server-a}";
        String historyIndex = "test-agent:run:history:session:{ses_index_reservation}";
        String marker = "test-agent:run:runtime-user:{usr_index_reservation}";

        store.initialize(manifest, input(manifest.runId()));

        InOrder order = inOrder(zsets, values, redis);
        order.verify(zsets).add(userIndex, manifest.runId().value(), maximumScore);
        order.verify(redis).execute(
                argThat(script -> isTtlExtension(script)),
                eq(List.of(userIndex)),
                eq(Long.toString(maximumRetention.toMillis())));
        order.verify(values).set(sessionIndex, manifest.runId().value(), maximumRetention);
        order.verify(zsets).add(serverIndex, manifest.runId().value(), maximumScore);
        order.verify(redis).execute(
                argThat(script -> isTtlExtension(script)),
                eq(List.of(serverIndex)),
                eq(Long.toString(maximumRetention.toMillis())));
        order.verify(zsets).add(historyIndex, manifest.runId().value(), manifest.updatedAt().toEpochMilli());
        order.verify(redis).execute(
                argThat(script -> isTtlExtension(script)),
                eq(List.of(historyIndex)),
                eq(Long.toString(maximumRetention.toMillis())));
        order.verify(values).set(marker, "1", maximumRetention);
        order.verify(redis).execute(
                argThat(script -> isInitialize(script)),
                anyList(),
                any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void serverRecoveryReadRenewsAllCrossSlotIndexes() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ZSetOperations<String, String> zsets = mock(ZSetOperations.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForZSet()).thenReturn(zsets);
        when(redis.opsForHash()).thenReturn(hashes);
        RunRuntimeManifest manifest = manifest();
        String serverIndex = "test-agent:run:active:server:{server-a}";
        when(zsets.rangeByScore(serverIndex, NOW.toEpochMilli(), Double.POSITIVE_INFINITY))
                .thenReturn(Set.of(manifest.runId().value()));
        when(hashes.entries("test-agent:run:{run_index_reservation}:manifest"))
                .thenReturn(manifestFields(manifest));
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
        Duration maximumRetention = Duration.ofMinutes(70);

        assertThat(store.findActiveByServer("server-a")).containsExactly(manifest);

        long maximumScore = NOW.plus(maximumRetention).toEpochMilli();
        org.mockito.Mockito.verify(zsets).add(
                "test-agent:run:active:user:{usr_index_reservation}", manifest.runId().value(), maximumScore);
        org.mockito.Mockito.verify(values).set(
                "test-agent:run:active:session:{ses_index_reservation}",
                manifest.runId().value(),
                maximumRetention);
        org.mockito.Mockito.verify(zsets).add(serverIndex, manifest.runId().value(), maximumScore);
    }

    private Map<Object, Object> manifestFields(RunRuntimeManifest manifest) throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        LinkedHashMap<Object, Object> fields = new LinkedHashMap<>();
        fields.put("base", mapper.writeValueAsString(manifest));
        fields.put("status", manifest.status().name());
        fields.put("statusVersion", Long.toString(manifest.statusVersion()));
        fields.put("lastSeq", Long.toString(manifest.lastSeq()));
        fields.put("earliestSeq", Long.toString(manifest.earliestSeq()));
        fields.put("resetGeneration", Long.toString(manifest.resetGeneration()));
        fields.put("detailsTruncated", manifest.detailsTruncated() ? "1" : "0");
        fields.put("durableEventCount", Long.toString(manifest.durableEventCount()));
        fields.put("detailBytes", Long.toString(manifest.detailBytes()));
        fields.put("attention", "");
        fields.put("attentionEventId", "");
        fields.put("attentionAt", "");
        fields.put("detailsExpiresAt", manifest.detailsExpiresAt().toString());
        fields.put("updatedAt", manifest.updatedAt().toString());
        fields.put("rootRemoteSessionId", manifest.rootRemoteSessionId());
        return fields;
    }

    private boolean isTtlExtension(RedisScript<?> script) {
        return script != null && script.getScriptAsString().contains("local current = redis.call('PTTL'");
    }

    private boolean isInitialize(RedisScript<?> script) {
        return script != null && script.getScriptAsString().contains("'inputBytes', ARGV[8]");
    }

    private RunRuntimeManifest manifest() {
        return new RunRuntimeManifest(
                new RunId("run_index_reservation"),
                RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_index_reservation"),
                new SessionId("ses_index_reservation"),
                new WorkspaceId("wrk_index_reservation"),
                "opencode",
                "req_index_reservation",
                "msg_index_reservation",
                "server-a",
                "backend-a",
                "node-a",
                "process-a",
                "remote-a",
                RunStatus.RUNNING,
                0L,
                0L,
                1L,
                0L,
                false,
                0L,
                0L,
                null,
                null,
                null,
                NOW.plus(Duration.ofMinutes(5)),
                NOW,
                NOW);
    }

    private RunRuntimeInput input(RunId runId) {
        return new RunRuntimeInput(runId, "prompt", List.of(), "msg_index_reservation", NOW);
    }
}
