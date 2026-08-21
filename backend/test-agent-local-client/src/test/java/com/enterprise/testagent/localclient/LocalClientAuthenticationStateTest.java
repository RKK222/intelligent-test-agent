package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientAuthenticationStateTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldPersistAndClearManualReEnrollmentRequirement() throws Exception {
        Path stateDirectory = temporaryDirectory.resolve("state");
        LocalClientStateStore store = new LocalClientStateStore(stateDirectory);

        store.requireReEnrollment();

        assertThat(new LocalClientStateStore(stateDirectory).reEnrollmentRequired()).isTrue();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(
                stateDirectory.resolve("re-enrollment-required"))))
                .isEqualTo("rw-------");

        store.clearReEnrollmentRequirement();

        assertThat(store.reEnrollmentRequired()).isFalse();
    }

    @Test
    void shouldOnlyStopReconnectForUnauthenticatedRegistrationErrors() {
        LocalClientFrameCodec codec = new LocalClientFrameCodec();
        LocalClientFrame unauthenticated = error(codec, "UNAUTHENTICATED", null);
        LocalClientFrame networkSideError = error(codec, "OPENCODE_BAD_GATEWAY", 7L);

        assertThat(LocalClientConnection.isRegistrationAuthenticationFailure(unauthenticated, codec, 0))
                .isTrue();
        assertThat(LocalClientConnection.isRegistrationAuthenticationFailure(networkSideError, codec, 7))
                .isFalse();
    }

    private static LocalClientFrame error(LocalClientFrameCodec codec, String code, Long generation) {
        return new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.ERROR,
                "lce_test",
                "trace_test",
                generation,
                codec.payload(new LocalClientPayloads.Error(code, "hidden", false, Map.of())));
    }
}
