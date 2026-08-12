package com.enterprise.testagent.localclient.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class LocalClientFrameCodecTest {

    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();

    @Test
    void shouldRoundTripRegisterFrameWithoutGeneration() {
        LocalClientPayloads.Register payload = new LocalClientPayloads.Register(
                "tack_v1_secret", "lci_device", "Mac", "darwin", "arm64", "1.0.0", "1.18.4", List.of());
        LocalClientFrame frame = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.REGISTER,
                "req-1",
                "trace-1",
                null,
                codec.payload(payload));

        LocalClientFrame decoded = codec.decode(codec.encode(frame));

        assertThat(decoded.type()).isEqualTo(LocalClientFrameType.REGISTER);
        assertThat(codec.payload(decoded, LocalClientPayloads.Register.class).clientInstanceId())
                .isEqualTo("lci_device");
    }

    @Test
    void shouldRejectUnsupportedVersionAndMissingGeneration() {
        LocalClientFrame unsupported = new LocalClientFrame(
                "local-opencode-client.v2",
                LocalClientFrameType.REGISTER,
                "req-1",
                "trace-1",
                null,
                codec.payload(new LocalClientPayloads.Cancel("request", "test")));
        LocalClientFrame missingGeneration = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.HEARTBEAT,
                "req-2",
                "trace-2",
                null,
                codec.payload(new LocalClientPayloads.Cancel("request", "test")));

        assertThatThrownBy(() -> codec.encode(unsupported))
                .isInstanceOf(LocalClientProtocolException.class)
                .hasMessageContaining("版本不兼容");
        assertThatThrownBy(() -> codec.encode(missingGeneration))
                .isInstanceOf(LocalClientProtocolException.class)
                .hasMessageContaining("connectionGeneration");
    }

    @Test
    void shouldRejectChunkAboveProtocolLimit() {
        int encodedBytes = (int) (4L * ((LocalClientProtocol.BINARY_CHUNK_BYTES + 2L) / 3L)) + 1;

        assertThatThrownBy(() -> new LocalClientPayloads.BinaryChunk(0, "x".repeat(encodedBytes), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 KiB");
    }
}
