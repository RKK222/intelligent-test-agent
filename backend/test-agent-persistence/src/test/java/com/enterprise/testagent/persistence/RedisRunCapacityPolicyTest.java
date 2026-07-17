package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRuntimeInput;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** Redis 详情容量截断必须保留可重建对话的最小关键快照。 */
class RedisRunCapacityPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");

    @Test
    void appendAndTransientScriptsProtectUserInputLatestAssistantAndRunStatus() throws Exception {
        for (String field : List.of("APPEND_SCRIPT", "PROJECT_SCRIPT")) {
            String script = script(field);

            assertThat(script)
                    .contains("local function protectedSnapshotEntry", "p:user-input:", "p:run-status:",
                            "latestAssistantMessage", "latestVisiblePart", "role == 'assistant'",
                            "partType == 'text'", "field == 'text'", "ARGV[35]", "projectionCompacted")
                    .containsSubsequence("evicted = 1", "resetGeneration", "detailsTruncated', '1'");
        }
    }

    @Test
    void nonSnapshotRuntimeDataLeavesFourMiBForCriticalSnapshot() throws Exception {
        RedisRunRuntimeStore store = store(32L * 1024L * 1024L, 100);
        Method method = RedisRunRuntimeStore.class.getDeclaredMethod("nonSnapshotDetailBudgetBytes");
        method.setAccessible(true);

        long budget = (long) method.invoke(store);

        assertThat(budget).isEqualTo(28L * 1024L * 1024L);
    }

    @Test
    void inputSnapshotUsesDedicatedProtectedProjectionKey() throws Exception {
        RedisRunRuntimeStore store = store(1024 * 1024L, 100);
        RunRuntimeInput input = input(new RunId("run_capacity_input"), "user request");
        Method method = RedisRunRuntimeStore.class.getDeclaredMethod("inputSnapshotKey", RunRuntimeInput.class);
        method.setAccessible(true);

        String key = (String) method.invoke(store, input);

        assertThat(key).startsWith("p:user-input:");
    }

    @Test
    void initializationReservesHalfCapacityForTerminalMaterializedSnapshot() {
        RedisRunRuntimeStore store = store(2048L, 100);
        RunRuntimeManifest manifest = manifest("run_capacity_reserve");

        assertThatThrownBy(() -> store.initialize(
                        manifest,
                        input(manifest.runId(), "x".repeat(700))))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void oversizedUserSnapshotKeepsBoundedVisibleRoleAndText() throws Exception {
        RedisRunRuntimeStore store = store(2048L, 100);
        Method method = RedisRunRuntimeStore.class.getDeclaredMethod("snapshotJson", RunEventDraft.class);
        method.setAccessible(true);
        RunEventDraft draft = new RunEventDraft(
                new RunId("run_capacity_user_snapshot"),
                RunEventType.MESSAGE_UPDATED,
                "trace_capacity",
                NOW,
                Map.of(
                        "messageId", "msg_input",
                        "role", "user",
                        "text", "x".repeat(10_000),
                        "message", Map.of("id", "msg_input", "role", "user", "text", "x".repeat(10_000))));

        String json = (String) method.invoke(store, draft);
        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) serialized.get("payload");

        assertThat(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(341);
        assertThat(payload).containsEntry("role", "user");
        assertThat(String.valueOf(payload.get("text"))).startsWith("x");
        assertThat(payload).containsEntry("snapshotTruncated", true);
    }

    private RedisRunRuntimeStore store(long maxDetailBytes, int snapshotLimit) {
        return new RedisRunRuntimeStore(
                mock(StringRedisTemplate.class),
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
                Duration.ofMinutes(30),
                100,
                maxDetailBytes,
                snapshotLimit);
    }

    private RunRuntimeManifest manifest(String value) {
        return new RunRuntimeManifest(
                new RunId(value),
                RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_capacity"),
                new SessionId("ses_capacity"),
                new WorkspaceId("wrk_capacity"),
                "opencode",
                "req_capacity",
                "msg_dispatch_capacity",
                "server-a",
                "bjp_capacity",
                "node_capacity",
                "ocp_capacity",
                "remote-capacity",
                RunStatus.RUNNING,
                1,
                0,
                1,
                0,
                false,
                0,
                0,
                null,
                null,
                null,
                NOW.plusSeconds(3600),
                NOW,
                NOW);
    }

    private RunRuntimeInput input(RunId runId, String prompt) {
        return new RunRuntimeInput(runId, prompt, List.of(Map.of("type", "text")), "msg_input", NOW);
    }

    private String script(String fieldName) throws ReflectiveOperationException {
        Field field = RedisRunRuntimeStore.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        RedisScript<?> script = (RedisScript<?>) field.get(null);
        return script.getScriptAsString();
    }
}
