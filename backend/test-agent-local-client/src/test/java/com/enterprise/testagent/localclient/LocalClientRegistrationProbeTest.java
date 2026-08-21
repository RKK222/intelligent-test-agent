package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientRegistrationProbeTest {

    @TempDir
    Path temporaryDirectory;

    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();

    @Test
    void shouldAcceptOnlyRegisteredResponseWithMatchingPositiveGeneration() throws Exception {
        LocalClientRegistrationProbe probe = probe((uri, request) -> new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.REGISTERED,
                request.requestId(),
                request.traceId(),
                7L,
                codec.payload(new LocalClientPayloads.Registered(7L, "grant", Instant.now(), Instant.now()))));

        assertThatCode(() -> probe.verify(credentials())).doesNotThrowAnyException();
    }

    @Test
    void shouldCollapseServerAuthenticationDetailsIntoOneFailure() throws Exception {
        LocalClientRegistrationProbe probe = probe((uri, request) -> new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.ERROR,
                "lce_test",
                request.traceId(),
                null,
                codec.payload(new LocalClientPayloads.Error(
                        "UNAUTHENTICATED", "wrong unified auth id", false, Map.of("secret", "detail")))));

        assertThatThrownBy(() -> probe.verify(credentials()))
                .isInstanceOf(LocalClientEnrollment.AuthenticationException.class)
                .hasMessage("本地客户端认证失败")
                .hasMessageNotContaining("unified")
                .hasMessageNotContaining("detail");
    }

    private LocalClientRegistrationProbe probe(LocalClientRegistrationProbe.Transport transport) throws Exception {
        return new LocalClientRegistrationProbe(
                configuration(),
                new LocalClientStateStore(temporaryDirectory.resolve("state")),
                LocalClientBuildInfo.resolve("20260820153045"),
                transport);
    }

    private LocalClientCredentialFile.Credentials credentials() {
        return LocalClientCredentialFile.credentials("UC-001", "tack_v1_secret");
    }

    private LocalClientConfiguration configuration() {
        return new LocalClientConfiguration(
                URI.create("https://platform.example.internal"),
                "client-test",
                temporaryDirectory.resolve("opencode"),
                temporaryDirectory.resolve("config"),
                temporaryDirectory.resolve("data"),
                4096,
                4195,
                false);
    }
}
