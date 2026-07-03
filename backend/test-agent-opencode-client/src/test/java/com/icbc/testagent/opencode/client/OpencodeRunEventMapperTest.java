package com.icbc.testagent.opencode.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventScopeContext;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpencodeRunEventMapperTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private static final RunId RUN_ID = new RunId("run_1234567890abcdef");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpencodeRunEventMapper mapper = new OpencodeRunEventMapper(objectMapper, () -> NOW);

    @Test
    void mapsRootIdleStatusToSessionStatusAndRunSucceeded() throws Exception {
        List<RunEventDraft> drafts = mapper.toDrafts(
                objectMapper.readTree("""
                        {"id":"evt_raw_status_idle","type":"session.status","properties":{"sessionID":"ses_root","status":{"type":"idle"}}}
                        """),
                RUN_ID,
                "trace_1234567890abcdef",
                rootScope());

        assertThat(drafts).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.SESSION_STATUS, RunEventType.RUN_SUCCEEDED);
        assertThat(drafts.getFirst().payload())
                .containsEntry("rootSessionId", "ses_root")
                .containsEntry("sessionId", "ses_root")
                .containsEntry("isChildSession", false)
                .containsEntry("scopeVersion", 1L);
    }

    @Test
    void derivedRunTerminalDoesNotReuseRawEventId() throws Exception {
        List<RunEventDraft> drafts = mapper.toDrafts(
                objectMapper.readTree("""
                        {"id":"evt_raw_status_idle","type":"session.status","properties":{"sessionID":"ses_root","status":{"type":"idle"}}}
                        """),
                RUN_ID,
                "trace_1234567890abcdef",
                rootScope());

        assertThat(drafts.get(0).payload()).containsEntry("rawEventId", "evt_raw_status_idle");
        assertThat(drafts.get(1).payload()).doesNotContainKey("rawEventId");
    }

    @Test
    void derivedRunTerminalPayloadCarriesSourceMetadata() throws Exception {
        List<RunEventDraft> drafts = mapper.toDrafts(
                objectMapper.readTree("""
                        {"id":"evt_raw_status_idle","type":"session.status","properties":{"sessionID":"ses_root","status":{"type":"idle"}}}
                        """),
                RUN_ID,
                "trace_1234567890abcdef",
                rootScope());

        assertThat(drafts.get(1).payload())
                .containsEntry("derived", true)
                .containsEntry("derivedFromRawType", "session.status")
                .containsEntry("derivedFromRawEventId", "evt_raw_status_idle")
                .doesNotContainKey("rawEventId");
    }

    @Test
    void mapsChildIdleStatusToSessionStatusOnly() throws Exception {
        List<RunEventDraft> drafts = mapper.toDrafts(
                objectMapper.readTree("""
                        {"id":"evt_raw_child_idle","type":"session.status","properties":{"sessionID":"ses_child","status":{"type":"idle"}}}
                        """),
                RUN_ID,
                "trace_1234567890abcdef",
                childScope());

        assertThat(drafts).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.SESSION_STATUS);
        assertThat(drafts.getFirst().payload())
                .containsEntry("rootSessionId", "ses_root")
                .containsEntry("sessionId", "ses_child")
                .containsEntry("parentSessionId", "ses_root")
                .containsEntry("isChildSession", true);
    }

    @Test
    void mapsRootSessionErrorToSessionErrorAndRunFailed() throws Exception {
        List<RunEventDraft> drafts = mapper.toDrafts(
                objectMapper.readTree("""
                        {"id":"evt_raw_error","type":"session.error","properties":{"sessionID":"ses_root","error":{"message":"boom"}}}
                        """),
                RUN_ID,
                "trace_1234567890abcdef",
                rootScope());

        assertThat(drafts).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.SESSION_ERROR, RunEventType.RUN_FAILED);
    }

    @Test
    void mapsChildSessionErrorToSessionErrorOnly() throws Exception {
        List<RunEventDraft> drafts = mapper.toDrafts(
                objectMapper.readTree("""
                        {"id":"evt_raw_child_error","type":"session.error","properties":{"sessionID":"ses_child","error":{"message":"boom"}}}
                        """),
                RUN_ID,
                "trace_1234567890abcdef",
                childScope());

        assertThat(drafts).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.SESSION_ERROR);
    }

    @Test
    void doesNotMapStepEndedToRunSucceededAfterScopeSupport() throws Exception {
        List<RunEventDraft> drafts = mapper.toDrafts(
                objectMapper.readTree("""
                        {"id":"evt_raw_step_ended","type":"session.next.step.ended","properties":{"sessionID":"ses_root","messageID":"msg_remote_1"}}
                        """),
                RUN_ID,
                "trace_1234567890abcdef",
                rootScope());

        assertThat(drafts).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.OPENCODE_EVENT_UNKNOWN);
    }

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
    void missingRawEventIdDoesNotWriteUnknownSentinel() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"type":"session.next.tool.called","properties":{"tool":"bash"}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.payload()).doesNotContainKey("rawEventId");
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
    void mapsSessionDiffToSessionDiff() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_diff","type":"session.diff","properties":{"files":["README.md"]}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.SESSION_DIFF);
    }

    @Test
    void mapsOpencodeAppMessageEventsToPlatformMessageEvents() throws Exception {
        RunEventDraft message = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_message","type":"message.updated","properties":{"messageID":"msg_1"}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");
        RunEventDraft part = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_part","type":"message.part.updated","properties":{"partID":"part_1"}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");
        RunEventDraft delta = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_delta","type":"message.part.delta","properties":{"delta":"hello"}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(message.type()).isEqualTo(RunEventType.MESSAGE_UPDATED);
        assertThat(part.type()).isEqualTo(RunEventType.MESSAGE_PART_UPDATED);
        assertThat(delta.type()).isEqualTo(RunEventType.MESSAGE_PART_DELTA);
        assertThat(delta.payload()).containsEntry("text", "hello");
    }

    @Test
    void mapsPermissionQuestionAndRuntimeStatusEvents() throws Exception {
        assertThat(mapType("permission.asked")).isEqualTo(RunEventType.PERMISSION_ASKED);
        assertThat(mapType("permission.replied")).isEqualTo(RunEventType.PERMISSION_REPLIED);
        assertThat(mapType("question.asked")).isEqualTo(RunEventType.QUESTION_ASKED);
        assertThat(mapType("question.replied")).isEqualTo(RunEventType.QUESTION_REPLIED);
        assertThat(mapType("question.rejected")).isEqualTo(RunEventType.QUESTION_REJECTED);
        assertThat(mapType("todo.updated")).isEqualTo(RunEventType.TODO_UPDATED);
        assertThat(mapType("vcs.branch.updated")).isEqualTo(RunEventType.VCS_BRANCH_UPDATED);
        assertThat(mapType("lsp.updated")).isEqualTo(RunEventType.LSP_UPDATED);
        assertThat(mapType("mcp.tools.changed")).isEqualTo(RunEventType.MCP_TOOLS_CHANGED);
    }

    @Test
    void mapsReviewChecklistEventTable() throws Exception {
        Map<String, RunEventType> expectedTypes = new LinkedHashMap<>();
        expectedTypes.put("message.updated", RunEventType.MESSAGE_UPDATED);
        expectedTypes.put("message.removed", RunEventType.MESSAGE_REMOVED);
        expectedTypes.put("message.part.updated", RunEventType.MESSAGE_PART_UPDATED);
        expectedTypes.put("message.part.removed", RunEventType.MESSAGE_PART_REMOVED);
        expectedTypes.put("message.part.delta", RunEventType.MESSAGE_PART_DELTA);
        expectedTypes.put("session.created", RunEventType.SESSION_CREATED);
        expectedTypes.put("session.updated", RunEventType.SESSION_UPDATED);
        expectedTypes.put("session.deleted", RunEventType.SESSION_DELETED);
        expectedTypes.put("session.diff", RunEventType.SESSION_DIFF);
        expectedTypes.put("permission.v2.asked", RunEventType.PERMISSION_ASKED);
        expectedTypes.put("permission.v2.replied", RunEventType.PERMISSION_REPLIED);
        expectedTypes.put("question.v2.asked", RunEventType.QUESTION_ASKED);
        expectedTypes.put("question.v2.replied", RunEventType.QUESTION_REPLIED);
        expectedTypes.put("question.v2.rejected", RunEventType.QUESTION_REJECTED);
        expectedTypes.put("vcs.branch.updated", RunEventType.VCS_BRANCH_UPDATED);
        expectedTypes.put("lsp.updated", RunEventType.LSP_UPDATED);
        expectedTypes.put("mcp.tools.changed", RunEventType.MCP_TOOLS_CHANGED);
        expectedTypes.put("reference.updated", RunEventType.REFERENCE_UPDATED);
        expectedTypes.put("file.edited", RunEventType.FILE_EDITED);
        expectedTypes.put("file.watcher.updated", RunEventType.FILE_WATCHER_UPDATED);

        for (Map.Entry<String, RunEventType> entry : expectedTypes.entrySet()) {
            assertThat(mapType(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        }
    }

    @Test
    void commonPayloadAliasesAreAddedForFrontendContract() throws Exception {
        RunEventDraft draft = mapper.toDrafts(
                objectMapper.readTree("""
                        {
                          "id":"evt_raw_alias",
                          "type":"message.part.updated",
                          "properties":{
                            "sessionID":"ses_root",
                            "messageID":"msg_1",
                            "partID":"part_1",
                            "callID":"call_1",
                            "requestID":"req_1"
                          }
                        }
                        """),
                RUN_ID,
                "trace_1234567890abcdef",
                rootScope()).getFirst();

        assertThat(draft.payload())
                .containsEntry("sessionId", "ses_root")
                .containsEntry("messageId", "msg_1")
                .containsEntry("partId", "part_1")
                .containsEntry("callId", "call_1")
                .containsEntry("requestId", "req_1");
    }

    @Test
    void syncEventEnvelopeUsesNestedEventTypeIdAndData() throws Exception {
        RunEventDraft draft = mapper.toDrafts(
                objectMapper.readTree("""
                        {
                          "payload": {
                            "type": "sync",
                            "syncEvent": {
                              "id": "evt_raw_task",
                              "type": "message.part.updated.1",
                              "seq": 27,
                              "aggregateID": "ses_root",
                              "data": {
                                "sessionID": "ses_root",
                                "messageID": "msg_task",
                                "part": {
                                  "id": "part_task",
                                  "messageID": "msg_task",
                                  "sessionID": "ses_root",
                                  "type": "tool",
                                  "tool": "task",
                                  "callID": "call_task",
                                  "state": {"status": "pending", "input": {}, "raw": ""}
                                }
                              }
                            }
                          }
                        }
                        """),
                RUN_ID,
                "trace_1234567890abcdef",
                rootScope()).getFirst();

        assertThat(draft.type()).isEqualTo(RunEventType.MESSAGE_PART_UPDATED);
        assertThat(draft.payload()).containsEntry("rawEventId", "evt_raw_task")
                .containsEntry("rawType", "message.part.updated")
                .containsEntry("sessionID", "ses_root")
                .containsEntry("messageID", "msg_task")
                .containsEntry("messageId", "msg_task");
        assertThat(draft.payload().get("part")).isInstanceOf(Map.class);
    }

    @Test
    void mapsStepEndedToUnknownLegacyDraft() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_done","type":"session.next.step.ended","properties":{"messageID":"msg_remote_1"}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.OPENCODE_EVENT_UNKNOWN);
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

        assertThat(draft.type()).isEqualTo(RunEventType.SESSION_STATUS);
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

        assertThat(draft.type()).isEqualTo(RunEventType.SESSION_STATUS);
    }

    @Test
    void mapsSessionStatusBusyToSessionStatus() throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw_status_busy","type":"session.status","properties":{"sessionID":"ses_remote_1","status":{"type":"busy"}}}
                        """),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");

        assertThat(draft.type()).isEqualTo(RunEventType.SESSION_STATUS);
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

        assertThat(draft.type()).isEqualTo(RunEventType.SESSION_ERROR);
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

    private RunEventType mapType(String rawType) throws Exception {
        RunEventDraft draft = mapper.toDraft(
                objectMapper.readTree("""
                        {"id":"evt_raw","type":"%s","properties":{"value":1}}
                        """.formatted(rawType)),
                new RunId("run_1234567890abcdef"),
                "trace_1234567890abcdef");
        return draft.type();
    }

    private RunEventScopeContext rootScope() {
        return new RunEventScopeContext(
                RUN_ID,
                "ses_root",
                "ses_root",
                null,
                false,
                null,
                null,
                null,
                1L,
                true);
    }

    private RunEventScopeContext childScope() {
        return new RunEventScopeContext(
                RUN_ID,
                "ses_root",
                "ses_child",
                "ses_root",
                true,
                "msg_task",
                "part_task",
                "call_task",
                2L,
                true);
    }
}
