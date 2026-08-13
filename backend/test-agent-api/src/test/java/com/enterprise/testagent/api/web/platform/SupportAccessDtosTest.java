package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupportAccessDtosTest {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

    @Test
    void workspaceAvailabilityDistinguishesOnlineOfflineUnboundAndUnknown() {
        BackendJavaProcess online = backend("server-online", BackendJavaProcessStatus.READY);

        assertThat(response("server-online", Map.of("server-online", online), true).backendAvailability())
                .isEqualTo("ONLINE");
        assertThat(response("server-offline", Map.of(), true).backendAvailability())
                .isEqualTo("OFFLINE");
        assertThat(response(null, Map.of(), true).backendAvailability())
                .isEqualTo("UNBOUND");
        assertThat(response("server-unknown", Map.of(), false).backendAvailability())
                .isEqualTo("UNKNOWN");
        SupportAccessDtos.WorkspaceResponse response = response(
                "server-online", Map.of("server-online", online), true);
        assertThat(response.rootPath()).isEqualTo("workspace:wrk_support_status");
        assertThat(response.physicalRootPath()).isNull();
    }

    private SupportAccessDtos.WorkspaceResponse response(
            String linuxServerId,
            Map<String, BackendJavaProcess> liveBackends,
            boolean backendStateKnown) {
        Workspace workspace = new Workspace(
                new WorkspaceId("wrk_support_status"),
                "排查工作区",
                "/tmp/support",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                linuxServerId,
                "trace_1234567890abcdef");
        return SupportAccessDtos.WorkspaceResponse.from(
                workspace,
                liveBackends,
                "server-current",
                backendStateKnown,
                ManagedWorkspacePathResolver.legacyOnly());
    }

    private BackendJavaProcess backend(String linuxServerId, BackendJavaProcessStatus status) {
        return new BackendJavaProcess(
                new BackendProcessId("bjp_support_status"),
                new LinuxServerId(linuxServerId),
                "http://127.0.0.1:8080",
                status,
                NOW,
                NOW,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }
}
