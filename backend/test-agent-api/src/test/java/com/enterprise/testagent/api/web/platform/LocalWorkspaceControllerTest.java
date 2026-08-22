package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.localclient.LocalWorkspaceApplicationService;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 回归本地工作区根目录 RPC 不能在 reactor-http event-loop 上同步等待。 */
class LocalWorkspaceControllerTest {

    @Test
    void createRunsBlockingTunnelWorkflowOnBoundedElastic() {
        UserId userId = new UserId("usr_local_workspace");
        LocalClientInstanceId instanceId = new LocalClientInstanceId("lci_local_workspace");
        LocalWorkspaceApplicationService service = mock(LocalWorkspaceApplicationService.class);
        LocalClientConnectionRoute route = mock(LocalClientConnectionRoute.class);
        BackendJavaProcess backend = mock(BackendJavaProcess.class);
        BackendProcessId backendProcessId = new BackendProcessId("bjp_local_workspace");
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        when(service.requireOwnedOnlineRoute(userId, instanceId)).thenAnswer(ignored -> {
            assertThat(Thread.currentThread().getName()).contains("boundedElastic");
            return route;
        });
        when(route.backendProcessId()).thenReturn(backendProcessId);
        when(backend.backendProcessId()).thenReturn(backendProcessId);
        when(routes.requireBackend(backendProcessId)).thenReturn(backend);
        when(routes.isCurrent(backendProcessId)).thenReturn(true);
        when(service.create(userId, instanceId, "project", "/Users/test/project", "trace_local_workspace"))
                .thenReturn(new LocalWorkspaceApplicationService.LocalWorkspaceView(
                        "wrk_local_workspace", "project", "/Users/test/project",
                        RuntimeKind.LOCAL_CLIENT, instanceId.value(), true, Map.of()));
        WebTestClient client = WebTestClient.bindToController(new LocalWorkspaceController(
                        service, routes, mock(BackendHttpForwarder.class)))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                            "token", userId, "user", "888888888", List.of("USER"),
                            Instant.now(), Instant.now().plusSeconds(3600)));
                    return chain.filter(exchange);
                })
                .build();

        client.post()
                .uri("/api/internal/platform/workspace-management/local-workspaces")
                .header("X-Trace-Id", "trace_local_workspace")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"clientInstanceId":"lci_local_workspace","name":"project","rootPath":"/Users/test/project"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workspaceId").isEqualTo("wrk_local_workspace");
    }
}
