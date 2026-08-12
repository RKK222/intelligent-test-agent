package com.enterprise.testagent.opencode.runtime.localclient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceBinding;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRuntimeTarget;
import com.enterprise.testagent.domain.session.SessionRuntimeTargetRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalRuntimeCapabilityGuardTest {

    private final LocalClientWorkspaceRepository workspaceRepository = mock(LocalClientWorkspaceRepository.class);
    private final SessionRuntimeTargetRepository sessionTargetRepository = mock(SessionRuntimeTargetRepository.class);
    private final LocalRuntimeCapabilityGuard guard =
            new LocalRuntimeCapabilityGuard(workspaceRepository, sessionTargetRepository);

    @Test
    void localWorkspaceCapabilityFailsClosed() {
        WorkspaceId workspaceId = new WorkspaceId("wrk_local1234567890");
        Instant now = Instant.parse("2026-08-12T00:00:00Z");
        when(workspaceRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(
                new LocalClientWorkspaceBinding(
                        workspaceId,
                        new UserId("usr_owner"),
                        new LocalClientInstanceId("lci_1234567890abcdef"),
                        "/tmp/workspace",
                        "sha256-root",
                        "file-store-key",
                        now,
                        now)));

        assertThatThrownBy(() -> guard.requireWorkspaceSupported(
                        workspaceId, "attachments", "不支持附件"))
                .isInstanceOf(PlatformException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void localSessionCapabilityDoesNotFallBackWhenClientIsOffline() {
        SessionId sessionId = new SessionId("ses_local1234567890");
        when(sessionTargetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(
                new SessionRuntimeTarget(
                        sessionId,
                        RuntimeKind.LOCAL_CLIENT,
                        new LocalClientInstanceId("lci_1234567890abcdef"))));

        assertThatThrownBy(() -> guard.requireSessionSupported(
                        sessionId, "terminal", "不支持终端"))
                .isInstanceOf(PlatformException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void serverAndHistoricalTargetsRemainCompatible() {
        WorkspaceId workspaceId = new WorkspaceId("wrk_server123456789");
        SessionId sessionId = new SessionId("ses_server123456789");
        when(workspaceRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.empty());
        when(sessionTargetRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatCode(() -> guard.requireWorkspaceSupported(workspaceId, "agentConfig", "不支持"))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.requireSessionSupported(sessionId, "collaboration", "不支持"))
                .doesNotThrowAnyException();
    }
}
