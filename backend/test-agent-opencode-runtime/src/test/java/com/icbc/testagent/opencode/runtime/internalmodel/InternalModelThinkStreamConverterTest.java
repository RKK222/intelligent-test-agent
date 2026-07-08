package com.icbc.testagent.opencode.runtime.internalmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class InternalModelThinkStreamConverterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void convertsThinkContentAcrossChunks() throws Exception {
        InternalModelThinkStreamConverter converter = new InternalModelThinkStreamConverter(objectMapper);

        JsonNode first = payload(converter.convertLine(
                "data:{\"choices\":[{\"delta\":{\"content\":\"<think>\\n\\nHere\",\"role\":\"assistant\"},\"index\":0}]}"));
        JsonNode second = payload(converter.convertLine(
                "data:{\"choices\":[{\"delta\":{\"content\":\" is reasoning\"},\"index\":0}]}"));
        JsonNode closePrefix = payload(converter.convertLine(
                "data:{\"choices\":[{\"delta\":{\"content\":\"</thi\"},\"index\":0}]}"));
        JsonNode closeSuffix = payload(converter.convertLine(
                "data:{\"choices\":[{\"delta\":{\"content\":\"nk>\\n\\n我是通义\"},\"index\":0}]}"));

        assertThat(first.at("/choices/0/delta/reasoning_content").asText()).isEqualTo("\n\nHere");
        assertThat(first.at("/choices/0/delta/content").isMissingNode()).isTrue();
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

    private JsonNode payload(String line) throws Exception {
        assertThat(line).startsWith("data:");
        return objectMapper.readTree(line.substring("data:".length()));
    }
}
