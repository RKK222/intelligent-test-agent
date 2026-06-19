package com.example.testagent.opencode.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.event.RunEventType;
import com.example.testagent.domain.run.RunId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OpencodeRunEventMapperTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpencodeRunEventMapper mapper = new OpencodeRunEventMapper(objectMapper, () -> NOW);

    @Test
    void mapsToolCalledToToolStarted() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_tool","type":"session.next.tool.called","properties":{"tool":"bash"}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.TOOL_STARTED);
        assertThat(draft.payload()).containsEntry("tool", "bash");
    }

    @Test
    void mapsToolSuccessToToolFinished() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_tool","type":"session.next.tool.success","properties":{"tool":"bash"}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.TOOL_FINISHED);
        assertThat(draft.payload()).containsEntry("status", "success");
    }

    @Test
    void mapsSessionDiffToDiffProposed() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_diff","type":"session.diff","properties":{"files":["README.md"]}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.DIFF_PROPOSED);
    }

    @Test
    void mapsStepEndedToRunSucceeded() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_done","type":"session.next.step.ended","properties":{"messageID":"msg_remote_1"}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.RUN_SUCCEEDED);
        assertThat(draft.payload()).containsEntry("messageID", "msg_remote_1");
    }

    @Test
    void mapsSessionIdleToRunSucceeded() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_idle","type":"session.idle","properties":{"sessionID":"ses_remote_1"}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.RUN_SUCCEEDED);
        assertThat(draft.payload()).containsEntry("sessionID", "ses_remote_1");
    }

    @Test
    void mapsSessionStatusIdleToRunSucceeded() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_status_idle","type":"session.status","properties":{"sessionID":"ses_remote_1","status":{"type":"idle"}}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.RUN_SUCCEEDED);
    }

    @Test
    void keepsSessionStatusBusyAsUnknown() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_status_busy","type":"session.status","properties":{"sessionID":"ses_remote_1","status":{"type":"busy"}}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.OPENCODE_EVENT_UNKNOWN);
    }

    @Test
    void mapsStepFailedToRunFailed() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_failed","type":"session.next.step.failed","properties":{"messageID":"msg_remote_1"}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.RUN_FAILED);
    }

    @Test
    void mapsSessionErrorToRunFailed() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_error","type":"session.error","properties":{"error":{"message":"boom"}}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.RUN_FAILED);
    }

    @Test
    void mapsUnknownEventWithoutDroppingRawContext() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_unknown","type":"future.event","properties":{"value":1}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.OPENCODE_EVENT_UNKNOWN);
        assertThat(draft.payload()).containsEntry("rawType", "future.event");
        assertThat(draft.payload()).containsKey("rawPayload");
    }
}
