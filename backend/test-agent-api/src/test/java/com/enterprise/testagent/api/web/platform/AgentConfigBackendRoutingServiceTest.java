package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.workspace.AgentConfigApplicationService;
import com.enterprise.testagent.workspace.AgentConfigResponses;
import com.enterprise.testagent.workspace.WorkspaceServerIdentity;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class AgentConfigBackendRoutingServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-24T00:00:00Z");
    private static final AuthPrincipal PRINCIPAL = new AuthPrincipal(
            "token",
            new com.enterprise.testagent.domain.user.UserId("usr_admin"),
            "admin",
            "AUTH_ADMIN",
            List.of("SUPER_ADMIN"),
            NOW,
            NOW.plusSeconds(3600));

    @Test
    void publicRepositoriesDeduplicateRemoteBackendsByLinuxServerId() {
        AgentConfigApplicationService service = org.mockito.Mockito.mock(AgentConfigApplicationService.class);
        when(service.localPublicRepositoryStatus(PRINCIPAL.userId())).thenReturn(new AgentConfigResponses.PublicRepositoryStatusResponse(
                "10.8.0.12",
                "10.8.0.12",
                "/data/config",
                "/data/config/opencode",
                "/data/configdev",
                "READY",
                true,
                true,
                "main",
                "commit_local",
                null));
        FakeHeartbeatStore heartbeatStore = new FakeHeartbeatStore(List.of(
                backendSnapshot("bjp_1234567890abcdef", "10.8.0.22"),
                backendSnapshot("bjp_2234567890abcdef", "10.8.0.22")));
        RecordingHttpClient httpClient = new RecordingHttpClient("""
                {"success":true,"traceId":"trace_agent_config_forward","data":{"linuxServerId":"10.8.0.22","serverName":"10.8.0.22","gitRootPath":"/data/config","configDirPath":"/data/config/opencode","worktreeRootPath":"/data/configdev","status":"READY","initialized":true,"initializationAllowed":true,"currentBranch":"main","commitHash":"commit_remote","message":null}}
                """);
        AgentConfigBackendRoutingService routingService = new AgentConfigBackendRoutingService(
                service,
                new WorkspaceServerIdentity("10.8.0.12"),
                heartbeatStore,
                new ObjectMapper().findAndRegisterModules(),
                httpClient);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                        .get("/api/internal/platform/workspace-management/agent-config/public/repositories")
                        .header("X-Trace-Id", "trace_agent_config_forward"));
        exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, PRINCIPAL);
        List<AgentConfigResponses.PublicRepositoryStatusResponse> responses = routingService.listPublicRepositories(
                exchange,
                "trace_agent_config_forward");

        assertThat(responses).extracting(AgentConfigResponses.PublicRepositoryStatusResponse::linuxServerId)
                .containsExactly("10.8.0.12", "10.8.0.22");
        assertThat(httpClient.requestedUris).hasSize(1);
    }

    private static BackendRuntimeSnapshot backendSnapshot(String backendProcessId, String linuxServerId) {
        LinuxServerId serverId = new LinuxServerId(linuxServerId);
        return new BackendRuntimeSnapshot(
                new LinuxServer(
                        serverId,
                        linuxServerId,
                        LinuxServerStatus.READY,
                        Map.of(),
                        NOW,
                        NOW,
                        NOW,
                        "trace_1234567890abcdef"),
                new BackendJavaProcess(
                        new BackendProcessId(backendProcessId),
                        serverId,
                        "http://" + linuxServerId + ":8080",
                        BackendJavaProcessStatus.READY,
                        NOW,
                        NOW,
                        NOW,
                        NOW,
                        "trace_1234567890abcdef"));
    }

    private record FakeHeartbeatStore(List<BackendRuntimeSnapshot> backendSnapshots) implements OpencodeProcessHeartbeatStore {
        @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) {}
        @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) {}
        @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) {}
        @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) {}
        @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return backendSnapshots; }
        @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
        @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
        @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
        @Override public void cleanupExpiredHeartbeats() {}
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final String responseBody;
        private final List<URI> requestedUris = new ArrayList<>();

        private RecordingHttpClient(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return null; }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
            requestedUris.add(request.uri());
            return new StringHttpResponse<>((T) responseBody, request);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record StringHttpResponse<T>(T body, HttpRequest request) implements HttpResponse<T> {
        @Override public int statusCode() { return 200; }
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (left, right) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
