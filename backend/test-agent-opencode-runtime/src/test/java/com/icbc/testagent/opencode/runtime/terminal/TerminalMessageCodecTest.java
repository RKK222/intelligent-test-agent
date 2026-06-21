package com.icbc.testagent.opencode.runtime.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

class TerminalMessageCodecTest {

    private final TerminalMessageCodec codec = new TerminalMessageCodec(new ObjectMapper());

    @Test
    void decodesInputResizeAndCloseMessages() {
        assertThat(codec.decode("""
                {"type":"input","data":"npm test\\n"}
                """)).isEqualTo(new TerminalClientMessage("input", "npm test\n", null, null, null));
        assertThat(codec.decode("""
                {"type":"resize","cols":120,"rows":32}
                """)).isEqualTo(new TerminalClientMessage("resize", null, 120, 32, null));
        assertThat(codec.decode("""
                {"type":"close","reason":"user"}
                """)).isEqualTo(new TerminalClientMessage("close", null, null, null, "user"));
    }

    @Test
    void decodesInvalidOrIncompleteClientMessagesAsValidationErrors() {
        assertThat(codec.decode("not-json"))
                .isEqualTo(new TerminalClientMessage("error", null, null, null, "invalid-json"));
        assertThat(codec.decode("""
                {"data":"missing type"}
                """)).isEqualTo(new TerminalClientMessage(null, "missing type", null, null, null));
        assertThat(codec.decode("""
                {"type":"unknown"}
                """)).isEqualTo(new TerminalClientMessage("unknown", null, null, null, null));
    }

    @Test
    void encodesOutputExitAndErrorMessages() throws Exception {
        assertThatJson(codec.encode(TerminalServerMessage.output("hello", 2)), "output", "hello", 2, null, null);
        assertThatJson(codec.encode(TerminalServerMessage.exit(0, 3)), "exit", null, 3, 0, null);
        assertThatJson(codec.encode(TerminalServerMessage.error("PTY_DENIED", "denied")), "error", null, null, "PTY_DENIED", "denied");
    }

    @Test
    void encodesTruncatedOutputAndWarningMessages() throws Exception {
        var truncated = new ObjectMapper().readTree(codec.encode(TerminalServerMessage.output("abc", 4, true)));
        assertThat(truncated.get("type").asText()).isEqualTo("output");
        assertThat(truncated.get("truncated").asBoolean()).isTrue();

        assertThatJson(
                codec.encode(TerminalServerMessage.warning("PTY_OUTPUT_TRUNCATED", "terminal output truncated")),
                "warning",
                null,
                null,
                "PTY_OUTPUT_TRUNCATED",
                "terminal output truncated");
    }

    private void assertThatJson(String raw, String type, String data, Integer seq, Object code, String message) throws Exception {
        var expected = JsonNodeFactory.instance.objectNode().put("type", type);
        if (data != null) {
            expected.put("data", data);
        }
        if (seq != null) {
            expected.put("seq", seq);
        }
        if (code instanceof Integer intCode) {
            expected.put("code", intCode);
        } else if (code instanceof String textCode) {
            expected.put("code", textCode);
        }
        if (message != null) {
            expected.put("message", message);
        }
        assertThat(new ObjectMapper().readTree(raw)).isEqualTo(expected);
    }
}
