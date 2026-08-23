package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientRegistrationFramesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldCreateEnrollmentRegistrationWithPairedIdentityAndManagedCapability() throws Exception {
        LocalClientStateStore stateStore = new LocalClientStateStore(temporaryDirectory.resolve("state"));
        LocalClientBuildInfo buildInfo = LocalClientBuildInfo.resolve("20260820153045");
        LocalClientFrameCodec codec = new LocalClientFrameCodec();

        LocalClientFrame frame = LocalClientRegistrationFrames.create(
                configuration(true),
                LocalClientCredentialFile.credentials("UC-001", "tack_v1_secret"),
                stateStore.read(),
                buildInfo,
                "lcr_test",
                "trace_test");
        LocalClientPayloads.Register register = codec.payload(frame, LocalClientPayloads.Register.class);

        assertThat(frame.type()).isEqualTo(LocalClientFrameType.REGISTER);
        assertThat(frame.connectionGeneration()).isNull();
        assertThat(register.unifiedAuthId()).isEqualTo("UC-001");
        assertThat(register.clientKey()).isEqualTo("tack_v1_secret");
        assertThat(register.clientInstanceId()).isEqualTo(stateStore.read().clientInstanceId());
        assertThat(register.clientVersion()).isEqualTo("20260820153045");
        assertThat(register.capabilities()).containsExactly(
                "SELF_UPDATE_V1", "OPENCODE_OBSERVABILITY_V1", "PUBLIC_CAPABILITY_SYNC_V1",
                "MANAGED_MODEL_CONFIG_V1");
    }

    @Test
    void shouldNotAdvertiseUpdaterWhenManagedJarLacksTrustedUpdaterConfiguration() throws Exception {
        LocalClientStateStore stateStore = new LocalClientStateStore(temporaryDirectory.resolve("state-unconfigured"));
        LocalClientFrameCodec codec = new LocalClientFrameCodec();

        LocalClientFrame frame = LocalClientRegistrationFrames.create(
                configuration(false),
                LocalClientCredentialFile.credentials("UC-001", "tack_v1_secret"),
                stateStore.read(),
                LocalClientBuildInfo.resolve("20260820153045"),
                "lcr_test",
                "trace_test");

        assertThat(codec.payload(frame, LocalClientPayloads.Register.class).capabilities())
                .containsExactly("OPENCODE_OBSERVABILITY_V1", "PUBLIC_CAPABILITY_SYNC_V1",
                        "MANAGED_MODEL_CONFIG_V1");
        assertThat(LocalClientConnection.shouldStartVersionChecks(
                configuration(false), LocalClientBuildInfo.resolve("20260820153045"))).isFalse();
        assertThat(LocalClientConnection.shouldStartVersionChecks(
                configuration(true), LocalClientBuildInfo.resolve("20260820153045"))).isTrue();
    }

    @Test
    void shouldOnlyQuarantineExplicitNonRetryableDigestConflict() {
        assertThat(LocalClientConnection.isDigestConflict(new LocalClientPayloads.Error(
                "CONFLICT",
                "safe message",
                false,
                Map.of("reason", "TRACE_CHUNK_DIGEST_CONFLICT")))).isTrue();
        assertThat(LocalClientConnection.isDigestConflict(new LocalClientPayloads.Error(
                "CONFLICT",
                "safe message",
                true,
                Map.of("reason", "TRACE_CHUNK_DIGEST_CONFLICT")))).isFalse();
        assertThat(LocalClientConnection.isDigestConflict(new LocalClientPayloads.Error(
                "CONFLICT",
                "safe message",
                false,
                Map.of("reason", "TRACE_UPLOAD_DECLARATION_MISSING")))).isFalse();
    }

    private LocalClientConfiguration configuration(boolean selfUpdateConfigured) {
        LocalClientConfiguration base = new LocalClientConfiguration(
                URI.create("https://platform.example.internal"),
                "client-test",
                temporaryDirectory.resolve("opencode"),
                temporaryDirectory.resolve("config"),
                temporaryDirectory.resolve("data"),
                4096,
                4195,
                false);
        if (!selfUpdateConfigured) {
            return base;
        }
        return new LocalClientConfiguration(
                base.serverBaseUri(),
                base.clientName(),
                base.opencodeExecutable(),
                base.opencodeConfigDirectory(),
                base.opencodeDataDirectory(),
                base.portMin(),
                base.portMax(),
                base.allowInsecureControl(),
                URI.create("http://downloads.example/local-opencode-client/"),
                "cHVibGljLWtleQ==",
                temporaryDirectory.resolve("runtime"));
    }
}
