package com.enterprise.testagent.localclient.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalClientFrameCodecTest {

    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();

    @Test
    void registeredModelConfigIsCapabilityGatedAndLegacyShapeOmitsTheField() {
        Instant now = Instant.parse("2026-08-23T08:00:00Z");

        assertThat(codec.payload(new LocalClientPayloads.Registered(7L, "grant", now, now))
                .has("managedModelConfig")).isFalse();
        assertThat(codec.payload(new LocalClientPayloads.Registered(
                7L,
                "grant",
                now,
                now,
                Map.of("model", "provider/model")))
                .has("managedModelConfig")).isTrue();
        assertThat(codec.payload(new LocalClientPayloads.Registered(
                7L,
                "grant",
                now,
                now,
                Map.of("model", "provider/model"),
                Map.of("rtkEnabled", false)))
                .path("managedRuntimeConfig").path("rtkEnabled").booleanValue()).isFalse();
    }

    @Test
    void shouldRoundTripRegisterFrameWithoutGeneration() {
        LocalClientPayloads.Register payload = new LocalClientPayloads.Register(
                "tack_v1_secret",
                "lci_device",
                "Kylin",
                "linux",
                "arm64",
                "20260820153045",
                "1.18.4",
                List.of(),
                "UC-001",
                "1",
                List.of("SELF_UPDATE_V1"));
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
        assertThat(codec.payload(decoded, LocalClientPayloads.Register.class).unifiedAuthId())
                .isEqualTo("UC-001");
        assertThat(codec.payload(decoded, LocalClientPayloads.Register.class).capabilities())
                .containsExactly("SELF_UPDATE_V1");
    }

    @Test
    void shouldDecodeLegacyRegisterWithoutNewOptionalFields() {
        String legacy = """
                {"protocolVersion":"local-opencode-client.v1","type":"REGISTER","requestId":"req-legacy",\
                "traceId":"trace-legacy","connectionGeneration":null,"payload":{\
                "clientKey":"tack_v1_secret","clientInstanceId":"lci_legacy","clientName":"legacy",\
                "platform":"linux","architecture":"arm64","clientVersion":"0.1.0",\
                "opencodeVersion":"1.18.4","reportedAddresses":[]}}
                """;

        LocalClientPayloads.Register decoded = codec.payload(
                codec.decode(legacy), LocalClientPayloads.Register.class);

        assertThat(decoded.unifiedAuthId()).isNull();
        assertThat(decoded.launcherVersion()).isNull();
        assertThat(decoded.capabilities()).isNull();
    }

    @Test
    void shouldIdentifyOnlyExactEightFieldZeroOneZeroRegisterAsLegacyAuthentication() {
        String legacy = """
                {"protocolVersion":"local-opencode-client.v1","type":"REGISTER","requestId":"req-legacy",\
                "traceId":"trace-legacy","connectionGeneration":null,"payload":{\
                "clientKey":"tack_v1_secret","clientInstanceId":"lci_legacy","clientName":"legacy",\
                "platform":"linux","architecture":"arm64","clientVersion":"0.1.0",\
                "opencodeVersion":"1.18.4","reportedAddresses":[]}}
                """;
        LocalClientFrame legacyFrame = codec.decode(legacy);
        LocalClientPayloads.Register legacyPayload = codec.payload(legacyFrame, LocalClientPayloads.Register.class);

        assertThat(LocalClientPayloads.isStrictLegacyRegister(legacyFrame.payload(), legacyPayload)).isTrue();

        LocalClientPayloads.Register modernPayload = new LocalClientPayloads.Register(
                "tack_v1_secret", "lci_modern", "modern", "linux", "arm64",
                "20260820120000", "1.18.4", List.of(), null, "1", List.of("SELF_UPDATE_V1"));
        assertThat(LocalClientPayloads.isStrictLegacyRegister(codec.payload(modernPayload), modernPayload)).isFalse();
    }

    @Test
    void shouldRoundTripVersionPolicyAndUpdateStatusFrames() {
        LocalClientFrame policy = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.VERSION_POLICY,
                "req-policy",
                "trace-policy",
                7L,
                codec.payload(new LocalClientPayloads.VersionPolicy(
                        "lci_device", 7L, "20260820163045", "UPDATE", 12L, true)));
        LocalClientFrame status = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.UPDATE_STATUS,
                "cmd-1",
                "trace-status",
                7L,
                codec.payload(new LocalClientPayloads.UpdateStatus(
                        "cmd-1", "lci_device", 7L, 12L,
                        "20260820163045", "UPDATE", "SUCCEEDED", null,
                        Instant.parse("2026-08-20T08:00:00Z"))));

        assertThat(codec.payload(codec.decode(codec.encode(policy)), LocalClientPayloads.VersionPolicy.class)
                .connectionGeneration()).isEqualTo(7L);
        LocalClientPayloads.UpdateStatus decodedStatus = codec.payload(
                codec.decode(codec.encode(status)), LocalClientPayloads.UpdateStatus.class);
        assertThat(decodedStatus.policyRevision()).isEqualTo(12L);
        assertThat(decodedStatus.direction()).isEqualTo("UPDATE");
        assertThat(decodedStatus.status()).isEqualTo("SUCCEEDED");

        LocalClientFrame ack = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.UPDATE_STATUS_ACK,
                "cmd-1",
                "trace-status",
                8L,
                codec.payload(new LocalClientPayloads.UpdateStatusAck(
                        "cmd-1", "lci_device", 7L, "SUCCEEDED")));
        LocalClientPayloads.UpdateStatusAck decodedAck = codec.payload(
                codec.decode(codec.encode(ack)), LocalClientPayloads.UpdateStatusAck.class);
        assertThat(decodedAck.connectionGeneration()).isEqualTo(7L);
        assertThat(decodedAck.status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void shouldRoundTripClientInitiatedWorkspaceRegistration() {
        LocalClientFrame request = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.WORKSPACE_REGISTER,
                "workspace-request",
                "trace-workspace",
                7L,
                codec.payload(new LocalClientPayloads.WorkspaceRegister(
                        "native-project", "/Users/test/native-project")));

        LocalClientFrame decoded = codec.decode(codec.encode(request));

        assertThat(decoded.type()).isEqualTo(LocalClientFrameType.WORKSPACE_REGISTER);
        assertThat(codec.payload(decoded, LocalClientPayloads.WorkspaceRegister.class))
                .isEqualTo(new LocalClientPayloads.WorkspaceRegister(
                        "native-project", "/Users/test/native-project"));
    }

    @Test
    void shouldRoundTripPublicCapabilityRecoveryCoordinatesAndChunkRequest() {
        String digest = "d".repeat(64);
        String commit = "c".repeat(40);
        String commandId = "lcpc_" + "a".repeat(32);
        LocalClientFrame version = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.PUBLIC_CAPABILITY_VERSION,
                "req-capability",
                "trace-capability",
                9L,
                codec.payload(new LocalClientPayloads.PublicCapabilityVersion(
                        "lci_device", 9L, commit, digest, commit, digest, commandId,
                        "ROLLED_BACK", "OPENCODE_ACTIVATION_FAILED", Instant.parse("2026-08-23T04:00:00Z"))));
        LocalClientFrame chunkRequest = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.PUBLIC_CAPABILITY_CHUNK_REQUEST,
                commandId,
                "trace-capability",
                9L,
                codec.payload(new LocalClientPayloads.PublicCapabilityChunkRequest(
                        commandId, "lci_device", 9L, digest, 3)));

        LocalClientPayloads.PublicCapabilityVersion decodedVersion = codec.payload(
                codec.decode(codec.encode(version)), LocalClientPayloads.PublicCapabilityVersion.class);
        LocalClientPayloads.PublicCapabilityChunkRequest decodedChunk = codec.payload(
                codec.decode(codec.encode(chunkRequest)), LocalClientPayloads.PublicCapabilityChunkRequest.class);

        assertThat(decodedVersion.pendingCommandId()).isEqualTo(commandId);
        assertThat(decodedVersion.status()).isEqualTo("ROLLED_BACK");
        assertThat(decodedChunk.connectionGeneration()).isEqualTo(9L);
        assertThat(decodedChunk.sequence()).isEqualTo(3);
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
