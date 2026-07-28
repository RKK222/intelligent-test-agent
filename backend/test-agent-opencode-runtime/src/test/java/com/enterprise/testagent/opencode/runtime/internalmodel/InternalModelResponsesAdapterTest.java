package com.enterprise.testagent.opencode.runtime.internalmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class InternalModelResponsesAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InternalModelResponsesAdapter adapter = new InternalModelResponsesAdapter(objectMapper);

    @Test
    void convertsCodexTextAndFunctionHistoryToChatCompletions() throws Exception {
        String request = """
                {
                  "model":"Qwen3.6-27B",
                  "instructions":"只读分析代码",
                  "input":[
                    {"type":"message","role":"user","content":[{"type":"input_text","text":"分析入口"}]},
                    {"type":"function_call","call_id":"call_1","name":"shell","arguments":"{\\\"cmd\\\":\\\"rg main\\\"}"},
                    {"type":"function_call_output","call_id":"call_1","output":"src/main.java"}
                  ],
                  "tools":[{
                    "type":"function","name":"shell","description":"只读命令",
                    "parameters":{"type":"object"},"strict":false
                  }],
                  "tool_choice":"auto",
                  "parallel_tool_calls":true,
                  "reasoning":{"effort":"medium"},
                  "store":false,
                  "stream":true,
                  "include":["reasoning.encrypted_content"]
                }
                """;

        InternalModelResponsesAdapter.ConvertedRequest converted = adapter.convertRequest(bytes(request));
        JsonNode body = objectMapper.readTree(converted.body());

        assertThat(converted.model()).isEqualTo("Qwen3.6-27B");
        assertThat(body.path("stream").asBoolean()).isTrue();
        assertThat(body.path("stream_options").path("include_usage").asBoolean()).isTrue();
        assertThat(body.path("messages")).hasSize(4);
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").get(2).path("tool_calls").get(0).path("id").asText())
                .isEqualTo("call_1");
        assertThat(body.path("messages").get(3).path("tool_call_id").asText()).isEqualTo("call_1");
        assertThat(body.path("tools").get(0).path("function").path("name").asText()).isEqualTo("shell");
        assertThat(body.has("reasoning")).isFalse();
        assertThat(body.has("include")).isFalse();
    }

    @Test
    void rejectsImagesFilesAndBuiltinTools() {
        String imageRequest = """
                {"model":"m","input":[{"type":"message","role":"user","content":[
                  {"type":"input_image","image_url":"file:///secret"}
                ]}],"stream":true}
                """;
        String webToolRequest = """
                {"model":"m","input":[],"tools":[{"type":"web_search_preview"}],"stream":true}
                """;

        assertValidationError(imageRequest, "message content 类型不受支持");
        assertValidationError(webToolRequest, "只支持 function tool");
    }

    @Test
    void emitsTextToolUsageAndCompletionEvents() throws Exception {
        InternalModelResponsesAdapter.StreamSession session = adapter.newStreamSession("Qwen3.6-27B");

        List<InternalModelResponsesAdapter.ResponseEvent> first = session.convertData("""
                {"choices":[{"delta":{"content":"结论："}}]}
                """);
        List<InternalModelResponsesAdapter.ResponseEvent> tool = session.convertData("""
                {"choices":[{"delta":{"tool_calls":[
                  {"index":0,"id":"call_1","function":{"name":"shell","arguments":"{\\\"cmd\\\":"}}
                ]}}]}
                """);
        session.convertData("""
                {"choices":[{"delta":{"tool_calls":[
                  {"index":0,"function":{"arguments":"\\\"rg main\\\"}"}}
                ]}}]}
                """);
        session.convertData("""
                {"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":4,"total_tokens":14,
                  "prompt_tokens_details":{"cached_tokens":3},
                  "completion_tokens_details":{"reasoning_tokens":2}}}
                """);
        List<InternalModelResponsesAdapter.ResponseEvent> completed = session.convertData("[DONE]");

        assertThat(first).extracting(InternalModelResponsesAdapter.ResponseEvent::event)
                .containsExactly("response.created", "response.output_text.delta");
        assertThat(tool).extracting(InternalModelResponsesAdapter.ResponseEvent::event)
                .containsExactly("response.function_call_arguments.delta");
        assertThat(completed).extracting(InternalModelResponsesAdapter.ResponseEvent::event)
                .containsExactly(
                        "response.output_item.done",
                        "response.output_item.done",
                        "response.completed");

        JsonNode functionItem = objectMapper.readTree(completed.get(1).data()).path("item");
        assertThat(functionItem.path("name").asText()).isEqualTo("shell");
        assertThat(functionItem.path("arguments").asText()).isEqualTo("{\"cmd\":\"rg main\"}");
        JsonNode response = objectMapper.readTree(completed.get(2).data()).path("response");
        assertThat(response.path("usage").path("input_tokens").asLong()).isEqualTo(10);
        assertThat(response.path("usage").path("input_tokens_details").path("cached_tokens").asLong())
                .isEqualTo(3);
        assertThat(response.path("usage").path("output_tokens_details").path("reasoning_tokens").asLong())
                .isEqualTo(2);
        assertThat(response.path("end_turn").asBoolean()).isFalse();
    }

    @Test
    void turnsMalformedUpstreamChunkIntoSanitizedFailureEvent() throws Exception {
        InternalModelResponsesAdapter.StreamSession session = adapter.newStreamSession("model");

        List<InternalModelResponsesAdapter.ResponseEvent> events = session.convertData("{not-json");

        assertThat(events).extracting(InternalModelResponsesAdapter.ResponseEvent::event)
                .containsExactly("response.created", "response.failed");
        JsonNode error = objectMapper.readTree(events.get(1).data()).path("response").path("error");
        assertThat(error.path("code").asText()).isEqualTo("upstream_malformed_sse");
        assertThat(events.get(1).data()).doesNotContain("not-json");
    }

    @Test
    void turnsInterruptedStreamIntoSanitizedFailureUnlessAlreadyCompleted() throws Exception {
        InternalModelResponsesAdapter.StreamSession interrupted = adapter.newStreamSession("model");
        interrupted.convertData("{\"choices\":[{\"delta\":{\"content\":\"半截结果\"}}]}");

        List<InternalModelResponsesAdapter.ResponseEvent> failed = interrupted.failIfIncomplete(
                "upstream_stream_interrupted",
                "上游模型流在完成前结束");

        assertThat(failed).extracting(InternalModelResponsesAdapter.ResponseEvent::event)
                .containsExactly("response.failed");
        JsonNode error = objectMapper.readTree(failed.get(0).data()).path("response").path("error");
        assertThat(error.path("code").asText()).isEqualTo("upstream_stream_interrupted");

        InternalModelResponsesAdapter.StreamSession completed = adapter.newStreamSession("model");
        completed.convertData("[DONE]");
        assertThat(completed.failIfIncomplete("ignored", "ignored")).isEmpty();
    }

    @Test
    void assemblesMultipleFunctionCallsAndRequestsCodexFollowUp() throws Exception {
        InternalModelResponsesAdapter.StreamSession session = adapter.newStreamSession("model");
        session.convertData("""
                {"choices":[{"delta":{"tool_calls":[
                  {"index":0,"id":"call_1","function":{"name":"shell","arguments":"{}"}},
                  {"index":1,"id":"call_2","function":{"name":"read_file","arguments":"{\\\"path\\\":\\\"A.java\\\"}"}}
                ]}}]}
                """);

        List<InternalModelResponsesAdapter.ResponseEvent> completed = session.convertData("[DONE]");

        assertThat(completed).extracting(InternalModelResponsesAdapter.ResponseEvent::event)
                .containsExactly(
                        "response.output_item.done",
                        "response.output_item.done",
                        "response.completed");
        assertThat(objectMapper.readTree(completed.get(0).data()).path("item").path("call_id").asText())
                .isEqualTo("call_1");
        assertThat(objectMapper.readTree(completed.get(1).data()).path("item").path("call_id").asText())
                .isEqualTo("call_2");
        assertThat(objectMapper.readTree(completed.get(2).data()).path("response").path("end_turn").asBoolean())
                .isFalse();
    }

    private void assertValidationError(String request, String expectedMessage) {
        assertThatThrownBy(() -> adapter.convertRequest(bytes(request)))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains(expectedMessage);
                });
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
