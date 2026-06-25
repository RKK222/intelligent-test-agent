package com.icbc.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagerControlMessageCodecTest {

    @Test
    void encodesAndDecodesRegisterMessage() {
        ManagerControlMessageCodec codec = new ManagerControlMessageCodec(new ObjectMapper());
        ManagerControlMessage message = ManagerControlMessage.register(
                "mgr_1234567890abcdef",
                "ctr_01",
                "10.8.0.12",
                "ctr_01",
                4096,
                4100,
                5,
                2,
                Map.of("commands", "start,health"),
                "trace_1234567890abcdef");

        ManagerControlMessage decoded = codec.decode(codec.encode(message));

        assertThat(decoded.type()).isEqualTo("register");
        assertThat(decoded.protocolVersion()).isEqualTo(ManagerControlProtocol.VERSION);
        assertThat(decoded.managerId()).isEqualTo("mgr_1234567890abcdef");
        assertThat(decoded.containerId()).isEqualTo("ctr_01");
        assertThat(decoded.linuxServerId()).isEqualTo("10.8.0.12");
        assertThat(decoded.currentProcesses()).isEqualTo(2);
        assertThat(decoded.capabilities()).containsEntry("commands", "start,health");
    }

    @Test
    void preservesUnknownTypeForHandlerValidation() {
        ManagerControlMessageCodec codec = new ManagerControlMessageCodec(new ObjectMapper());

        ManagerControlMessage decoded = codec.decode("""
                {"type":"mystery","protocolVersion":"opencode-manager.v1","traceId":"trace_1234567890abcdef"}
                """);

        assertThat(decoded.type()).isEqualTo("mystery");
        assertThat(decoded.traceId()).isEqualTo("trace_1234567890abcdef");
    }
}
