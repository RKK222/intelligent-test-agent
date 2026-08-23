package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalClientBuildInfoTest {

    @Test
    void shouldPreferExplicitBuildVersionAndExposeUpdaterCapability() {
        LocalClientBuildInfo buildInfo = LocalClientBuildInfo.resolve("20260820153045");

        assertThat(buildInfo.clientVersion()).isEqualTo("20260820153045");
        assertThat(buildInfo.launcherVersion()).isEqualTo("1");
        assertThat(buildInfo.capabilities()).containsExactly(
                "SELF_UPDATE_V1", "OPENCODE_OBSERVABILITY_V1", "PUBLIC_CAPABILITY_SYNC_V1",
                "MANAGED_MODEL_CONFIG_V1", "WORKSPACE_GIT_ACCESS_V1");
        assertThat(buildInfo.managedRelease()).isTrue();
    }

    @Test
    void shouldKeepDevelopmentFallbackOutsidePackagedJar() {
        LocalClientBuildInfo buildInfo = LocalClientBuildInfo.resolve(null);

        assertThat(buildInfo.clientVersion()).isEqualTo("0.1.0-dev");
        assertThat(buildInfo.capabilities()).containsExactly(
                "OPENCODE_OBSERVABILITY_V1", "PUBLIC_CAPABILITY_SYNC_V1", "MANAGED_MODEL_CONFIG_V1",
                "WORKSPACE_GIT_ACCESS_V1");
        assertThat(buildInfo.managedRelease()).isFalse();
    }

    @Test
    void shouldRenderCliVersionFromBuildInformation() {
        LocalClientBuildInfo buildInfo = LocalClientBuildInfo.resolve("20260820153045");

        assertThat(LocalClientMain.versionText(buildInfo))
                .isEqualTo("test-agent-local-client 20260820153045");
    }
}
