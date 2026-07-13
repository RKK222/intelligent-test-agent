package com.icbc.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SideQuestionEventProjectorTest {

    private static final String TEMPORARY_SESSION_ID = "ses_side1234567890abcdef";
    private static final RunId RUN_ID = new RunId("run_side1234567890abcdef");
    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");

    @Test
    void recursivelyReadsSessionIdAndDropsGlobalOrOtherSessionEvents() {
        SideQuestionEventProjector projector = new SideQuestionEventProjector(TEMPORARY_SESSION_ID);

        assertThat(projector.project(draft(
                RunEventType.TOOL_STARTED,
                Map.of("rawPayload", Map.of("properties", Map.of(
                        "sessionId", TEMPORARY_SESSION_ID,
                        "tool", "read"))))))
                .isEmpty();
        assertThat(projector.project(draft(
                RunEventType.TOOL_STARTED,
                Map.of("sessionID", "ses_main1234567890abcdef", "tool", "bash"))))
                .isEmpty();
        assertThat(projector.project(draft(RunEventType.TOOL_STARTED, Map.of("tool", "bash"))))
                .isEmpty();
    }

    @Test
    void ignoresInjectedScopeSessionIdAndRequiresActualRawEventSession() {
        SideQuestionEventProjector projector = new SideQuestionEventProjector(TEMPORARY_SESSION_ID);

        assertThat(projector.project(draft(
                RunEventType.TOOL_STARTED,
                Map.of(
                        "sessionId", TEMPORARY_SESSION_ID,
                        "tool", "bash",
                        "rawPayload", Map.of("type", "lsp.updated", "properties", Map.of())))))
                .isEmpty();
        assertThat(projector.project(draft(
                RunEventType.MESSAGE_UPDATED,
                Map.of(
                        "sessionId", TEMPORARY_SESSION_ID,
                        "rawPayload", Map.of("properties", Map.of("info", Map.of(
                                "id", "msg_other",
                                "sessionID", "ses_other1234567890abcdef",
                                "role", "assistant")))))))
                .isEmpty();
    }

    @Test
    void emitsDeltaOnlyAfterAssistantTextPartAssociation() {
        SideQuestionEventProjector projector = new SideQuestionEventProjector(TEMPORARY_SESSION_ID);

        assertThat(projector.project(message("msg_assistant", "assistant"))).isEmpty();
        assertThat(projector.project(part("msg_assistant", "part_text", "text"))).isEmpty();

        List<RunEventDraft> projected = projector.project(delta("msg_assistant", "part_text", "检查完成"));

        assertThat(projected).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(RunEventType.SIDE_QUESTION_DELTA);
            assertThat(event.payload()).containsExactly(Map.entry("delta", "检查完成"));
        });
    }

    @Test
    void preservesLeadingWhitespaceAndNewlineDeltaChunks() {
        SideQuestionEventProjector projector = new SideQuestionEventProjector(TEMPORARY_SESSION_ID);
        projector.project(message("msg_assistant", "assistant"));
        projector.project(part("msg_assistant", "part_text", "text"));

        assertThat(projector.project(delta("msg_assistant", "part_text", " world")))
                .extracting(event -> event.payload().get("delta"))
                .containsExactly(" world");
        assertThat(projector.project(delta("msg_assistant", "part_text", "\n")))
                .extracting(event -> event.payload().get("delta"))
                .containsExactly("\n");
    }

    @Test
    void userReasoningAndToolPartsNeverEnterDelta() {
        SideQuestionEventProjector projector = new SideQuestionEventProjector(TEMPORARY_SESSION_ID);

        projector.project(message("msg_user", "user"));
        projector.project(part("msg_user", "part_user", "text"));
        projector.project(message("msg_assistant", "assistant"));
        projector.project(part("msg_assistant", "part_reasoning", "reasoning"));
        projector.project(part("msg_assistant", "part_tool", "tool"));

        assertThat(projector.project(delta("msg_user", "part_user", "用户问题"))).isEmpty();
        assertThat(projector.project(delta("msg_assistant", "part_reasoning", "内部思考"))).isEmpty();
        assertThat(projector.project(draft(
                RunEventType.MESSAGE_PART_DELTA,
                Map.of(
                        "sessionID", TEMPORARY_SESSION_ID,
                        "messageID", "msg_assistant",
                        "partID", "part_tool",
                        "delta", "secret",
                        "input", Map.of("command", "pwd"),
                        "output", "/tmp/demo"))))
                .isEmpty();
    }

    @Test
    void correlatesAnswerByPostForkMessageBoundaryEvenWhenParentPointsToHistory() {
        SideQuestionEventProjector projector =
                new SideQuestionEventProjector(TEMPORARY_SESSION_ID, Set.of("msg_old_answer"));

        projector.project(draft(RunEventType.MESSAGE_UPDATED, Map.of(
                "rawPayload", Map.of("properties", Map.of("info", Map.of(
                        "id", "msg_old_answer",
                        "sessionID", TEMPORARY_SESSION_ID,
                        "parentID", "msg_old_prompt",
                        "role", "assistant"))))));
        assertThat(projector.hasObservedAnswerMessage()).isFalse();

        // OpenCode 1.17.7 的真实 fork 会让新 assistant.parentID 指向 fork 前最后一条 assistant，
        // 因此关联本轮答案必须看 fork 后的新 message ID，而不能要求 parentID 等于新 user message ID。
        projector.project(draft(RunEventType.MESSAGE_UPDATED, Map.of(
                "rawPayload", Map.of("properties", Map.of("info", Map.of(
                        "id", "msg_answer",
                        "sessionID", TEMPORARY_SESSION_ID,
                        "parentID", "msg_old_answer",
                        "role", "assistant"))))));
        assertThat(projector.hasObservedAnswerMessage()).isTrue();
        assertThat(projector.answerCompleted()).isFalse();

        projector.project(draft(RunEventType.MESSAGE_UPDATED, Map.of(
                "rawPayload", Map.of("properties", Map.of("info", Map.of(
                        "id", "msg_answer",
                        "sessionID", TEMPORARY_SESSION_ID,
                        "parentID", "msg_old_answer",
                        "role", "assistant",
                        "finish", "stop"))))));
        assertThat(projector.answerCompleted()).isTrue();
    }

    @Test
    void interleavedMainAndForkEventsOnlyEmitForkDelta() {
        SideQuestionEventProjector projector = new SideQuestionEventProjector(TEMPORARY_SESSION_ID);

        // 回归依据：opencode-source/opencode-1.17.8/packages/opencode/src/session/session.ts 的 Session.fork 不设置 parentID，
        // 因此不能按 parentID 推断 fork，必须按事件 payload 中的实际 sessionID/sessionId 精确过滤。
        projector.project(message("msg_fork", "assistant"));
        projector.project(part("msg_fork", "part_fork", "text"));
        assertThat(projector.project(draft(
                RunEventType.MESSAGE_PART_DELTA,
                Map.of(
                        "sessionID", "ses_main1234567890abcdef",
                        "messageID", "msg_main",
                        "partID", "part_main",
                        "delta", "主会话文本"))))
                .isEmpty();
        assertThat(projector.project(delta("msg_fork", "part_fork", "旁路文本")))
                .extracting(event -> event.payload().get("delta"))
                .containsExactly("旁路文本");
    }

    private static RunEventDraft message(String messageId, String role) {
        return draft(RunEventType.MESSAGE_UPDATED, Map.of(
                "rawPayload", Map.of("properties", Map.of("info", Map.of(
                        "id", messageId,
                        "sessionID", TEMPORARY_SESSION_ID,
                        "role", role)))));
    }

    private static RunEventDraft part(String messageId, String partId, String type) {
        return draft(RunEventType.MESSAGE_PART_UPDATED, Map.of(
                "rawPayload", Map.of(
                        "id", "evt_part_1234567890abcdef",
                        "type", "message.part.updated",
                        "properties", Map.of("part", Map.of(
                                "id", partId,
                                "messageID", messageId,
                                "sessionId", TEMPORARY_SESSION_ID,
                                "type", type)))));
    }

    private static RunEventDraft delta(String messageId, String partId, String text) {
        return draft(RunEventType.MESSAGE_PART_DELTA, Map.of(
                "rawPayload", Map.of("properties", Map.of(
                        "sessionID", TEMPORARY_SESSION_ID,
                        "messageID", messageId,
                        "partID", partId,
                        "delta", text))));
    }

    private static RunEventDraft draft(RunEventType type, Map<String, Object> payload) {
        return new RunEventDraft(RUN_ID, type, "trace_1234567890abcdef", NOW, payload);
    }
}
