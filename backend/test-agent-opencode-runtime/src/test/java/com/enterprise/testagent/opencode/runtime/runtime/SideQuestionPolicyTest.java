package com.enterprise.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.agent.runtime.AgentSessionMessage;
import com.enterprise.testagent.agent.runtime.AgentSessionMessagesResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SideQuestionPolicyTest {

    @Test
    void messageAndCharacterBoundariesAreSharedAndExact() {
        AgentSessionMessagesResult emptyText = context(40, "");
        int metadataCharacters = SideQuestionPolicy.estimateContextCharacters(emptyText);
        int boundaryTextLength = SideQuestionPolicy.CONTEXT_CHARACTER_LIMIT - metadataCharacters;

        assertThat(SideQuestionPolicy.shouldCompact(context(40, "x".repeat(boundaryTextLength)))).isFalse();
        assertThat(SideQuestionPolicy.shouldCompact(context(40, "x".repeat(boundaryTextLength + 1)))).isTrue();
        assertThat(SideQuestionPolicy.shouldCompact(context(41, ""))).isTrue();
    }

    @Test
    void synchronousAndStreamingEntrypointsUseOneQuestionLimitAndSecurityPrompt() {
        assertThat(SideQuestionPolicy.requireQuestion("x".repeat(SideQuestionPolicy.MAX_QUESTION_LENGTH)))
                .hasSize(SideQuestionPolicy.MAX_QUESTION_LENGTH);
        assertThatThrownByQuestionTooLong();
        assertThat(SideQuestionPolicy.SYSTEM_PROMPT)
                .contains("read-only")
                .contains("never edit files")
                .contains("Do not stop after a tool call");
        assertThat(SideQuestionPolicy.BUILD_AGENT).isEqualTo("build");
        assertThat(SideQuestionPolicy.TASK_TIMEOUT).isEqualTo(Duration.ofSeconds(120));
    }

    private void assertThatThrownByQuestionTooLong() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> SideQuestionPolicy.requireQuestion(
                        "x".repeat(SideQuestionPolicy.MAX_QUESTION_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AgentSessionMessagesResult context(int count, String text) {
        java.util.ArrayList<AgentSessionMessage> messages = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            messages.add(new AgentSessionMessage(
                    Map.of("role", "user"),
                    List.of(Map.of("type", "text", "text", index == 0 ? text : ""))));
        }
        return new AgentSessionMessagesResult(messages);
    }
}
