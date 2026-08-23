package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientPublicCapabilityUpdaterTest {

    private static final String INSTANCE_ID = "lci_public_capability_updater";
    private static final long GENERATION = 7L;
    private static final String COMMAND_ID = "lcpc_" + "a".repeat(32);
    private static final String COMMIT = "c".repeat(40);
    private static final String DIGEST = "d".repeat(64);

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsDigestFailureAndReportsTerminalStatus() {
        RecordingSink sink = new RecordingSink();
        LocalClientPublicCapabilityStore store = new LocalClientPublicCapabilityStore(temporaryDirectory);
        LocalClientPublicCapabilityUpdater updater = new LocalClientPublicCapabilityUpdater(
                store, mock(OpencodeProcessSupervisor.class), sink);
        updater.handleCommand(command(3, 1));

        updater.handleChunk(COMMAND_ID, new LocalClientPayloads.BinaryChunk(
                0, Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}), true));

        assertThat(store.snapshot().status()).isEqualTo("FAILED");
        assertThat(store.snapshot().errorCode()).isEqualTo("ARTIFACT_DIGEST_MISMATCH");
        assertThat(sink.lastStatus()).satisfies(status -> {
            assertThat(status.status()).isEqualTo("FAILED");
            assertThat(status.errorCode()).isEqualTo("ARTIFACT_DIGEST_MISMATCH");
        });
    }

    @Test
    void rejectsGenerationMismatchBeforeCreatingDownload() {
        RecordingSink sink = new RecordingSink();
        LocalClientPublicCapabilityUpdater updater = new LocalClientPublicCapabilityUpdater(
                new LocalClientPublicCapabilityStore(temporaryDirectory),
                mock(OpencodeProcessSupervisor.class),
                sink);
        LocalClientPayloads.PublicCapabilityUpdateCommand mismatched =
                new LocalClientPayloads.PublicCapabilityUpdateCommand(
                        COMMAND_ID, INSTANCE_ID, GENERATION + 1, COMMIT, DIGEST, "e".repeat(64),
                        3, 1, false, "{\"schemaVersion\":1}");

        assertThatThrownBy(() -> updater.handleCommand(mismatched))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("generation");
        assertThat(sink.frames).isEmpty();
    }

    @Test
    void outOfOrderChunkBecomesRecoverableFailedState() {
        RecordingSink sink = new RecordingSink();
        LocalClientPublicCapabilityStore store = new LocalClientPublicCapabilityStore(temporaryDirectory);
        LocalClientPublicCapabilityUpdater updater = new LocalClientPublicCapabilityUpdater(
                store, mock(OpencodeProcessSupervisor.class), sink);
        updater.handleCommand(command(3, 1));

        updater.handleChunk(COMMAND_ID, new LocalClientPayloads.BinaryChunk(1, "", true));

        assertThat(store.snapshot().status()).isEqualTo("FAILED");
        assertThat(store.snapshot().errorCode()).isEqualTo("CHUNK_SEQUENCE_MISMATCH");
        assertThat(sink.lastStatus().status()).isEqualTo("FAILED");
    }

    private static LocalClientPayloads.PublicCapabilityUpdateCommand command(long size, int chunks) {
        return new LocalClientPayloads.PublicCapabilityUpdateCommand(
                COMMAND_ID, INSTANCE_ID, GENERATION, COMMIT, DIGEST, "e".repeat(64),
                size, chunks, false, "{\"schemaVersion\":1}");
    }

    private static final class RecordingSink implements LocalClientPublicCapabilityUpdater.ProtocolSink {
        private final List<Frame> frames = new ArrayList<>();

        @Override
        public void send(LocalClientFrameType type, String requestId, Object payload) {
            frames.add(new Frame(type, requestId, payload));
        }

        @Override
        public String clientInstanceId() {
            return INSTANCE_ID;
        }

        @Override
        public long generation() {
            return GENERATION;
        }

        LocalClientPayloads.PublicCapabilityUpdateStatus lastStatus() {
            return frames.stream()
                    .filter(frame -> frame.type() == LocalClientFrameType.PUBLIC_CAPABILITY_UPDATE_STATUS)
                    .map(Frame::payload)
                    .map(LocalClientPayloads.PublicCapabilityUpdateStatus.class::cast)
                    .reduce((first, second) -> second)
                    .orElseThrow();
        }
    }

    private record Frame(LocalClientFrameType type, String requestId, Object payload) {
    }
}
