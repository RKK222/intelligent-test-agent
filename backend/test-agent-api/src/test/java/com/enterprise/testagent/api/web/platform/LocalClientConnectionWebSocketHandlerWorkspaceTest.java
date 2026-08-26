package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientConnectionRegistry;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientRegistrationService;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientTunnelGateway;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientUpdateCoordinator;
import com.enterprise.testagent.opencode.runtime.localclient.LocalWorkspaceApplicationService;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartupService;
import com.enterprise.testagent.system.management.localclient.LocalClientCredentialApplicationService;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

/** 验证客户端主动注册不会阻塞接收同连接上的根目录校验回包。 */
class LocalClientConnectionWebSocketHandlerWorkspaceTest {

    @Test
    void restoresAvailableWorkspacesAsynchronouslyAfterReconnect() throws Exception {
        UserId userId = new UserId("usr_recent_reconnect");
        LocalClientInstanceId instanceId = new LocalClientInstanceId("lci_recent_reconnect");
        LocalWorkspaceApplicationService workspaceService = mock(LocalWorkspaceApplicationService.class);
        CountDownLatch restored = new CountDownLatch(1);
        when(workspaceService.restoreAvailableOnReconnect(
                userId, instanceId, 13L, "trace-reconnect"))
                .thenAnswer(ignored -> {
                    restored.countDown();
                    return new LocalWorkspaceApplicationService.ReconnectRestoreResult(0, 0, 0);
                });
        LocalClientConnectionWebSocketHandler handler = handler(workspaceService);
        LocalClientConnectionWebSocketHandler.ConnectionState state =
                new LocalClientConnectionWebSocketHandler.ConnectionState(
                        userId, instanceId, 13, "grant-fingerprint", "trace-reconnect", true,
                        ConcurrentHashMap.newKeySet());

        handler.autoRestoreAvailableWorkspaces(state);

        assertThat(restored.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void releasesInboundFlowBeforeBlockingWorkspaceRegistrationCompletes() throws Exception {
        LocalClientFrameCodec codec = new LocalClientFrameCodec();
        UserId userId = new UserId("usr_local_workspace");
        LocalClientInstanceId instanceId = new LocalClientInstanceId("lci_local_workspace");
        LocalWorkspaceApplicationService workspaceService = mock(LocalWorkspaceApplicationService.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(workspaceService.create(
                userId, instanceId, "project", "/Users/test/project", "trace-workspace"))
                .thenAnswer(ignored -> {
                    entered.countDown();
                    assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
                    return new LocalWorkspaceApplicationService.LocalWorkspaceView(
                            "wrk_local_workspace",
                            "project",
                            "/Users/test/project",
                            RuntimeKind.LOCAL_CLIENT,
                            instanceId.value(),
                            true,
                            Map.of());
                });
        LocalClientConnectionWebSocketHandler handler = handler(workspaceService);
        LocalClientConnectionWebSocketHandler.ConnectionState state =
                new LocalClientConnectionWebSocketHandler.ConnectionState(
                        userId, instanceId, 7, "grant-fingerprint", "trace-register", true,
                        ConcurrentHashMap.newKeySet());
        LocalClientFrame request = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                LocalClientFrameType.WORKSPACE_REGISTER,
                "workspace-request",
                "trace-workspace",
                7L,
                codec.payload(new LocalClientPayloads.WorkspaceRegister("project", "/Users/test/project")));
        Sinks.Many<LocalClientFrame> outbound = Sinks.many().unicast().onBackpressureBuffer();

        StepVerifier.create(handler.handleAuthenticated(request, outbound, Sinks.one(), state))
                .verifyComplete();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        release.countDown();

        StepVerifier.create(outbound.asFlux().take(1))
                .assertNext(response -> {
                    assertThat(response.type()).isEqualTo(LocalClientFrameType.WORKSPACE_REGISTERED);
                    assertThat(response.requestId()).isEqualTo("workspace-request");
                    assertThat(codec.payload(response, LocalClientPayloads.WorkspaceRegistered.class))
                            .isEqualTo(new LocalClientPayloads.WorkspaceRegistered(
                                    "wrk_local_workspace", "project", "/Users/test/project"));
                })
                .verifyComplete();
    }

    private static LocalClientConnectionWebSocketHandler handler(
            LocalWorkspaceApplicationService workspaceService) {
        return new LocalClientConnectionWebSocketHandler(
                mock(LocalClientCredentialApplicationService.class),
                mock(LocalClientRegistrationService.class),
                mock(LocalClientConnectionRegistry.class),
                mock(LocalClientConnectionSupersessionService.class),
                mock(LocalClientTunnelGateway.class),
                mock(BackendJavaRouteResolver.class),
                mock(OpencodeProcessStartupService.class),
                mock(LocalClientUpdateCoordinator.class),
                workspaceService,
                mock(LocalClientControlSecuritySettings.class),
                new LocalClientAuthenticationRateLimiter(1, Duration.ofMinutes(1)));
    }
}
