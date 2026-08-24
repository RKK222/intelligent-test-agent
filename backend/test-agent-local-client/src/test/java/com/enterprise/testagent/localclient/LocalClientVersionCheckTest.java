package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientVersionCheckTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldBuildVersionCheckFromManagedReleaseAndStableInstance() throws Exception {
        LocalClientPersistentState state = new LocalClientStateStore(temporaryDirectory.resolve("state")).read();
        Instant checkedAt = Instant.parse("2026-08-20T10:00:00Z");

        LocalClientPayloads.VersionCheck check = LocalClientConnection.versionCheck(
                state,
                LocalClientBuildInfo.resolve("20260820153045"),
                checkedAt);

        assertThat(check.clientInstanceId()).isEqualTo(state.clientInstanceId());
        assertThat(check.clientVersion()).isEqualTo("20260820153045");
        assertThat(check.launcherVersion()).isEqualTo("1");
        assertThat(check.opencodeVersion()).isEqualTo("1.18.4");
        assertThat(check.capabilities()).containsExactly(
                "SELF_UPDATE_V1",
                "OPENCODE_OBSERVABILITY_V1",
                "PUBLIC_CAPABILITY_SYNC_V1",
                "MANAGED_MODEL_CONFIG_V1",
                "WORKSPACE_GIT_ACCESS_V1");
        assertThat(check.checkedAt()).isEqualTo(checkedAt);
    }

    @Test
    void shouldAddBoundedZeroToSixtySecondJitterToFiveMinuteCheck() {
        assertThat(LocalClientConnection.versionCheckDelaySeconds(0)).isEqualTo(300);
        assertThat(LocalClientConnection.versionCheckDelaySeconds(60)).isEqualTo(360);
    }

    @Test
    void shouldAcceptOnlyStrictLegacyEmptyVersionPolicySentWithoutConfiguredTarget() {
        LocalClientPayloads.VersionPolicy emptyPolicy = new LocalClientPayloads.VersionPolicy(
                "lci_version_policy", 7, null, "SAME", 0, false);

        assertThat(LocalClientConnection.acceptVersionPolicy("lci_version_policy", 7, emptyPolicy)).isTrue();
        assertThat(LocalClientConnection.acceptVersionPolicy(
                "lci_version_policy", 7,
                new LocalClientPayloads.VersionPolicy(
                        "lci_version_policy", 7, "20260824200000", "UPDATE", 0, false)))
                .isFalse();
        assertThat(LocalClientConnection.acceptVersionPolicy(
                "lci_version_policy", 7,
                new LocalClientPayloads.VersionPolicy(
                        "lci_version_policy", 7, null, "SAME", 0, true)))
                .isFalse();
    }

    @Test
    void shouldLogSafeRootFailureCodeInsteadOfCompletionWrapper() {
        assertThat(LocalClientConnection.connectionFailureCode(new CompletionException(
                new IllegalArgumentException("version policy coordinates are invalid"))))
                .isEqualTo("VERSION_POLICY_INVALID");
        assertThat(LocalClientConnection.connectionFailureCode(new CompletionException(
                new IllegalStateException("sensitive upstream detail"))))
                .isEqualTo("IllegalStateException");
    }
}
