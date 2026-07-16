package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.agent.runtime.AgentSessionMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunTurnMessageSelectorTest {

    private static final Instant RUN_CREATED_AT = Instant.parse("2026-07-15T14:24:36Z");
    private static final Instant RUN_UPDATED_AT = RUN_CREATED_AT.plusSeconds(30);

    @Test
    void missingExplicitDispatchDoesNotFallBackToTimestampCandidate() {
        List<AgentSessionMessage> messages = turn("msg_actual_user", "msg_actual_assistant", RUN_CREATED_AT.plusMillis(100));

        RunTurnMessageSelector.Selection selection = RunTurnMessageSelector.select(
                messages,
                "msg_expected_user",
                RUN_CREATED_AT,
                RUN_UPDATED_AT);

        assertThat(selection.resolved()).isFalse();
        assertThat(selection.messages()).isEmpty();
    }

    @Test
    void missingLegacyDispatchUsesOnlyUniqueUserInsideRunTimeWindow() {
        List<AgentSessionMessage> messages = List.of(
                user("msg_previous_user", RUN_CREATED_AT.minusSeconds(20)),
                assistant("msg_previous_assistant", "msg_previous_user", RUN_CREATED_AT.minusSeconds(19)),
                user("msg_current_user", RUN_CREATED_AT.plusMillis(100)),
                assistant("msg_current_assistant", "msg_current_user", RUN_CREATED_AT.plusSeconds(1)));

        RunTurnMessageSelector.Selection selection = RunTurnMessageSelector.select(
                messages,
                null,
                RUN_CREATED_AT,
                RUN_UPDATED_AT);

        assertThat(selection.resolved()).isTrue();
        assertThat(selection.userMessageId()).isEqualTo("msg_current_user");
        assertThat(selection.messages()).extracting(message -> message.message().get("id"))
                .containsExactly("msg_current_user", "msg_current_assistant");
    }

    @Test
    void missingLegacyDispatchRejectsAmbiguousUsersInsideRunTimeWindow() {
        List<AgentSessionMessage> messages = List.of(
                user("msg_candidate_one", RUN_CREATED_AT.plusMillis(100)),
                assistant("msg_assistant_one", "msg_candidate_one", RUN_CREATED_AT.plusSeconds(1)),
                user("msg_candidate_two", RUN_UPDATED_AT.plusSeconds(1)),
                assistant("msg_assistant_two", "msg_candidate_two", RUN_UPDATED_AT.plusSeconds(2)));

        RunTurnMessageSelector.Selection selection = RunTurnMessageSelector.select(
                messages,
                null,
                RUN_CREATED_AT,
                RUN_UPDATED_AT);

        assertThat(selection.resolved()).isFalse();
        assertThat(selection.messages()).isEmpty();
    }

    @Test
    void selectionExcludesAssistantWithoutDirectParentOwnership() {
        List<AgentSessionMessage> messages = List.of(
                user("msg_current_user", RUN_CREATED_AT.plusMillis(100)),
                new AgentSessionMessage(
                        Map.of(
                                "id", "msg_parentless_assistant",
                                "role", "assistant",
                                "time", Map.of("created", RUN_CREATED_AT.plusSeconds(1).toEpochMilli())),
                        List.of(Map.of("id", "part_parentless", "type", "tool", "tool", "todowrite"))));

        RunTurnMessageSelector.Selection selection = RunTurnMessageSelector.select(
                messages,
                "msg_current_user",
                RUN_CREATED_AT,
                RUN_UPDATED_AT);

        assertThat(selection.resolved()).isTrue();
        assertThat(selection.messages()).extracting(message -> message.message().get("id"))
                .containsExactly("msg_current_user");
    }

    private static List<AgentSessionMessage> turn(String userId, String assistantId, Instant createdAt) {
        return List.of(
                user(userId, createdAt),
                assistant(assistantId, userId, createdAt.plusMillis(100)));
    }

    private static AgentSessionMessage user(String id, Instant createdAt) {
        return new AgentSessionMessage(
                Map.of(
                        "id", id,
                        "role", "user",
                        "time", Map.of("created", createdAt.toEpochMilli())),
                List.of(Map.of("id", "part_" + id, "type", "text", "text", id)));
    }

    private static AgentSessionMessage assistant(String id, String parentId, Instant createdAt) {
        return new AgentSessionMessage(
                Map.of(
                        "id", id,
                        "parentID", parentId,
                        "role", "assistant",
                        "time", Map.of("created", createdAt.toEpochMilli())),
                List.of(Map.of("id", "part_" + id, "messageID", id, "type", "text", "text", id)));
    }
}
