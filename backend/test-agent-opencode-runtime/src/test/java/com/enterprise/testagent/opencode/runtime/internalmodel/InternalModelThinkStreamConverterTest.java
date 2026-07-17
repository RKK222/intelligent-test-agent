package com.enterprise.testagent.opencode.runtime.internalmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class InternalModelThinkStreamConverterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void convertsThinkContentAcrossChunks() throws Exception {
        InternalModelThinkStreamConverter converter = new InternalModelThinkStreamConverter(objectMapper);

        JsonNode openPrefix = payload(converter.convertLine(
                "data:{\"choices\":[{\"delta\":{\"content\":\"<thi\",\"role\":\"assistant\"},\"index\":0}]}"));
        JsonNode openSuffix = payload(converter.convertLine(
                "data:{\"choices\":[{\"delta\":{\"content\":\"nk>\\n\\nHere\"},\"index\":0}]}"));
        JsonNode second = payload(converter.convertLine(
                "data:{\"choices\":[{\"delta\":{\"content\":\" is reasoning\"},\"index\":0}]}"));
        JsonNode closePrefix = payload(converter.convertLine(
                "data:{\"choices\":[{\"delta\":{\"content\":\"</thi\"},\"index\":0}]}"));
        JsonNode closeSuffix = payload(converter.convertLine(
                "data:{\"choices\":[{\"delta\":{\"content\":\"nk>\\n\\n我是通义\"},\"index\":0}]}"));

        assertThat(openPrefix.at("/choices/0/delta/content").isMissingNode()).isTrue();
        assertThat(openPrefix.at("/choices/0/delta/reasoning_content").isMissingNode()).isTrue();
        assertThat(openSuffix.at("/choices/0/delta/reasoning_content").asText()).isEqualTo("\n\nHere");
        assertThat(openSuffix.at("/choices/0/delta/content").isMissingNode()).isTrue();
        assertThat(second.at("/choices/0/delta/reasoning_content").asText()).isEqualTo(" is reasoning");
        assertThat(closePrefix.at("/choices/0/delta/content").isMissingNode()).isTrue();
        assertThat(closePrefix.at("/choices/0/delta/reasoning_content").isMissingNode()).isTrue();
        assertThat(closeSuffix.at("/choices/0/delta/content").asText()).isEqualTo("\n\n我是通义");
    }

    @Test
    void preservesNormalTextAroundInlineThinkBlock() throws Exception {
        InternalModelThinkStreamConverter converter = new InternalModelThinkStreamConverter(objectMapper);

        JsonNode payload = payload(converter.convertLine(
                "data:{\"choices\":[{\"delta\":{\"content\":\"hello <think>abc</think> world\"},\"index\":0}]}"));

        assertThat(payload.at("/choices/0/delta/content").asText()).isEqualTo("hello  world");
        assertThat(payload.at("/choices/0/delta/reasoning_content").asText()).isEqualTo("abc");
    }

    @Test
    void preservesWholeDeltaWhenReasoningContentAlreadyExists() throws Exception {
        InternalModelThinkStreamConverter converter = new InternalModelThinkStreamConverter(objectMapper);

        converter.convertData("{\"choices\":[{\"delta\":{\"content\":\"<think>残留状态\"}}]}");

        JsonNode converted = objectMapper.readTree(converter.convertData(
                "{\"choices\":[{\"delta\":{\"content\":\"<think>思考</think>回答\","
                        + "\"reasoning_content\":\"已有\"}}]}"));
        JsonNode following = objectMapper.readTree(converter.convertData(
                "{\"choices\":[{\"delta\":{\"content\":\"后续正文\"}}]}"));

        assertThat(converted.at("/choices/0/delta/reasoning_content").asText())
                .isEqualTo("已有");
        assertThat(converted.at("/choices/0/delta/content").asText())
                .isEqualTo("<think>思考</think>回答");
        assertThat(following.at("/choices/0/delta/content").asText()).isEqualTo("后续正文");
        assertThat(following.at("/choices/0/delta/reasoning_content").isMissingNode()).isTrue();
    }

    @Test
    void preservesDonePayloadWhenDataWasAlreadyDecoded() {
        InternalModelThinkStreamConverter converter = new InternalModelThinkStreamConverter(objectMapper);

        assertThat(converter.convertData("[DONE]")).isEqualTo("[DONE]");
    }

    private JsonNode payload(String line) throws Exception {
        assertThat(line).startsWith("data:");
        return objectMapper.readTree(line.substring("data:".length()));
    }
}
