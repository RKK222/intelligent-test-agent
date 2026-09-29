package com.enterprise.testagent.opencode.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpencodeV2ContentAdapterTest {

    @Test
    void usesStableOrdinalForTextAndReasoning() {
        Map<String, Object> text = OpencodeV2ContentAdapter.project(
                Map.of("type", "text", "text", "answer"), "ses_1", "msg_1", 2);
        assertThat(text).containsEntry("id", "part_msg_1_2")
                .containsEntry("sessionID", "ses_1")
                .containsEntry("messageID", "msg_1");

        Map<String, Object> reasoning = OpencodeV2ContentAdapter.project(
                Map.of("type", "reasoning", "text", "thought", "time", Map.of("created", 10, "completed", 12)),
                "ses_1", "msg_1", 3);
        Map<?, ?> reasoningTime = (Map<?, ?>) reasoning.get("time");
        assertThat(reasoningTime.get("start")).isEqualTo(10);
        assertThat(reasoningTime.get("end")).isEqualTo(12);
    }

    @Test
    void preservesToolCallIdentityAndProjectsOutputForHistoryAndEvents() {
        Map<String, Object> tool = OpencodeV2ContentAdapter.project(
                Map.of("type", "tool", "id", "call_1", "name", "read",
                        "time", Map.of("created", 20, "completed", 30),
                        "state", Map.of("status", "completed", "input", Map.of("filePath", "a.txt"),
                                "content", List.of(Map.of("type", "text", "text", "line one"),
                                        Map.of("type", "text", "text", "line two")))),
                "ses_1", "msg_1", 0);
        assertThat(tool).containsEntry("id", "call_1")
                .containsEntry("callID", "call_1")
                .containsEntry("tool", "read")
                .containsEntry("output", "line one\nline two");
        Map<?, ?> state = (Map<?, ?>) tool.get("state");
        assertThat(state.get("output")).isEqualTo("line one\nline two");
        Map<?, ?> time = (Map<?, ?>) state.get("time");
        assertThat(time.get("start")).isEqualTo(20);
        assertThat(time.get("end")).isEqualTo(30);
    }
}
