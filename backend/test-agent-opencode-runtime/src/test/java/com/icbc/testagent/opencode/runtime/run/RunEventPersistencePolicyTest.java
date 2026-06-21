package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.Map.entry;

import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunId;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunEventPersistencePolicyTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private final RunEventPersistencePolicy policy = new RunEventPersistencePolicy();

    @Test
    void messageProjectionAndDeltaEventsAreTransientOnly() {
        for (RunEventType type : new RunEventType[]{
                RunEventType.ASSISTANT_MESSAGE_DELTA,
                RunEventType.MESSAGE_UPDATED,
                RunEventType.MESSAGE_REMOVED,
                RunEventType.MESSAGE_PART_UPDATED,
                RunEventType.MESSAGE_PART_REMOVED,
                RunEventType.MESSAGE_PART_DELTA}) {
            RunEventDraft draft = draft(type, Map.of("text", "hello"));

            assertThat(policy.shouldPersist(draft)).as(type.wireName()).isFalse();
        }
    }

    @Test
    void criticalStateEventsRemainDurable() {
        for (RunEventType type : new RunEventType[]{
                RunEventType.RUN_SUCCEEDED,
                RunEventType.RUN_FAILED,
                RunEventType.DIFF_PROPOSED,
                RunEventType.PERMISSION_ASKED,
                RunEventType.QUESTION_ASKED,
                RunEventType.TODO_UPDATED,
                RunEventType.TOOL_FINISHED}) {
            assertThat(policy.shouldPersist(draft(type, Map.of("status", "ok")))).as(type.wireName()).isTrue();
        }
    }

    @Test
    void toolFinishedPayloadIsSanitizedBeforePersistence() {
        RunEventDraft sanitized = policy.sanitizeForPersistence(draft(
                RunEventType.TOOL_FINISHED,
                Map.ofEntries(
                        entry("tool", "bash"),
                        entry("callID", "call_1"),
                        entry("messageID", "msg_1"),
                        entry("partID", "part_1"),
                        entry("status", "completed"),
                        entry("title", "Bash"),
                        entry("error", "exit 1"),
                        entry("rawPayload", Map.of("full", "event")),
                        entry("output", "very long output"),
                        entry("input", Map.of("command", "cat large.log")),
                        entry("metadata", Map.of("large", "metadata")))));

        assertThat(sanitized.payload()).containsEntry("tool", "bash");
        assertThat(sanitized.payload()).containsEntry("callID", "call_1");
        assertThat(sanitized.payload()).containsEntry("messageID", "msg_1");
        assertThat(sanitized.payload()).containsEntry("partID", "part_1");
        assertThat(sanitized.payload()).containsEntry("status", "completed");
        assertThat(sanitized.payload()).containsEntry("title", "Bash");
        assertThat(sanitized.payload()).containsEntry("error", "exit 1");
        assertThat(sanitized.payload()).doesNotContainKeys("rawPayload", "output", "input", "metadata");
    }

    @Test
    void rawPayloadIsRemovedFromAllPersistedOpencodeEvents() {
        RunEventDraft sanitized = policy.sanitizeForPersistence(draft(
                RunEventType.PERMISSION_ASKED,
                Map.of("requestID", "perm_1", "rawPayload", Map.of("secret", "body"))));

        assertThat(sanitized.payload()).containsEntry("requestID", "perm_1");
        assertThat(sanitized.payload()).doesNotContainKey("rawPayload");
    }

    private RunEventDraft draft(RunEventType type, Map<String, Object> payload) {
        return new RunEventDraft(
                new RunId("run_1234567890abcdef"),
                type,
                "trace_1234567890abcdef",
                NOW,
                payload);
    }
}
