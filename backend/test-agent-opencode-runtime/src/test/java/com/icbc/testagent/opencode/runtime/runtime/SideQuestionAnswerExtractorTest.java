package com.icbc.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SideQuestionAnswerExtractorTest {

    private final SideQuestionAnswerExtractor extractor = new SideQuestionAnswerExtractor();

    @Test
    void pureToolCallProtocolDoesNotBecomeAnswer() {
        assertThat(extractor.extract(Map.of(
                "parts", List.of(Map.of(
                        "type", "text",
                        "text", "<tool_calls:abc>\n<tool_call:abc>Bash\ncommand=ls\n</tool_calls:abc>")))))
                .isNull();
    }

    @Test
    void keepsNaturalLanguageAfterToolCallProtocol() {
        assertThat(extractor.extract(Map.of(
                "parts", List.of(Map.of(
                        "type", "text",
                        "text", "<tool_calls:abc>\n<tool_call:abc>Bash\ncommand=ls\n</tool_calls:abc>\n实际答案")))))
                .isEqualTo("实际答案");
    }

    @Test
    void projectedMessagesOnlyUseLastAssistantText() {
        Object messages = List.of(
                Map.of(
                        "info", Map.of("role", "user"),
                        "parts", List.of(Map.of("type", "text", "text", "用户问题"))),
                Map.of(
                        "info", Map.of("role", "assistant"),
                        "parts", List.of(Map.of("type", "text", "text", "较早答案"))),
                Map.of(
                        "info", Map.of("role", "assistant"),
                        "parts", List.of(
                                Map.of("type", "reasoning", "text", "内部思考"),
                                Map.of("type", "text", "text", "最终答案"))));

        assertThat(extractor.extract(messages)).isEqualTo("最终答案");
    }

    @Test
    void lastAssistantWithoutNaturalLanguageNeverFallsBackToHistoricalAnswer() {
        Object messages = List.of(
                Map.of(
                        "info", Map.of("role", "assistant"),
                        "parts", List.of(Map.of("type", "text", "text", "主会话历史答案"))),
                Map.of(
                        "info", Map.of("role", "assistant"),
                        "parts", List.of(Map.of(
                                "type", "text",
                                "text", "<tool_calls:abc>\n<tool_call:abc>Bash\ncommand=ls\n</tool_calls:abc>"))));

        assertThat(extractor.extract(messages)).isNull();
    }
}
