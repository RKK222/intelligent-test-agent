package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.ContainerManagerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
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

class RuntimeManagementBackendRoutingServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");

    @Test
    void forwardsManagedProcessCommandToBackendOwningContainerAndPreservesAuthTrace() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"success":true,"traceId":"trace_1234567890abcdef","data":{"command":"restart","status":"STARTED","port":4096,"pid":12346,"baseUrl":"http://10.8.0.22:4096","sessionPath":"/data/session/4096","configPath":"/data/config/opencode","healthy":true,"message":"opencode server started","traceId":"trace_1234567890abcdef"}}
                """);
        RuntimeManagementBackendRoutingService service = service(httpClient, heartbeatStore(
                List.of(backend("bjp_1234567890abcdef", "10.8.0.22", "http://10.8.0.22:18080", NOW)),
                List.of(managerSnapshot("ctr_01", "10.8.0.22"))));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/platform/opencode-runtime/management/containers/ctr_01/processes/4096/restart")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .build());

        Optional<String> target = service.forwardTargetForContainer(exchange, new OpencodeContainerId("ctr_01"));
        ApiResponse<RuntimeManagementDtos.ManagedProcessCommandResponse> response = service.forward(
                exchange,
                target.orElseThrow(),
                new TypeReference<>() {});

        assertThat(response.data().command()).isEqualTo("restart");
        assertThat(response.data().status()).isEqualTo("STARTED");
        assertThat(httpClient.requests).singleElement().satisfies(request -> {
            assertThat(request.uri().toString()).isEqualTo(
                    "http://10.8.0.22:18080/api/internal/platform/opencode-runtime/management/containers/ctr_01/processes/4096/restart");
            assertThat(request.headers().firstValue("X-Trace-Id")).contains("trace_1234567890abcdef");
            assertThat(request.headers().firstValue(org.springframework.http.HttpHeaders.AUTHORIZATION)).contains("Bearer user-token");
            assertThat(request.headers().firstValue(UserOpencodeBackendRoutingWebFilter.ROUTED_HEADER)).contains("true");
        });
    }

    @Test
    void routedHeaderSkipsRuntimeManagementForwardingToAvoidLoops() {
        RuntimeManagementBackendRoutingService service = service(new RecordingHttpClient(200, "{}"), heartbeatStore(
                List.of(backend("bjp_1234567890abcdef", "10.8.0.22", "http://10.8.0.22:18080", NOW)),
                List.of(managerSnapshot("ctr_01", "10.8.0.22"))));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/platform/opencode-runtime/management/containers/ctr_01/processes/4096/stop")
                .header(UserOpencodeBackendRoutingWebFilter.ROUTED_HEADER, "true")
                .build());

        Optional<String> target = service.forwardTargetForContainer(exchange, new OpencodeContainerId("ctr_01"));

        assertThat(target).isEmpty();
    }

    @Test
    void forwardsContainerCommandWithinSameLinuxServerWhenSelectedBackendIsAnotherJavaProcess() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"success":true,"traceId":"trace_1234567890abcdef","data":{"command":"stop","status":"STOPPED","port":4096,"healthy":false,"message":"stopped","traceId":"trace_1234567890abcdef"}}
                """);
        RuntimeManagementBackendRoutingService service = service(httpClient, heartbeatStore(
                List.of(backend("bjp_same_server_target", "10.8.0.21", "http://10.8.0.21:18080", NOW)),
                List.of(managerSnapshot("ctr_01", "10.8.0.21"))));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/platform/opencode-runtime/management/containers/ctr_01/processes/4096/stop")
                .build());

        Optional<String> target = service.forwardTargetForContainer(exchange, new OpencodeContainerId("ctr_01"));
        service.forward(exchange, target.orElseThrow(), new TypeReference<ApiResponse<RuntimeManagementDtos.ManagedProcessCommandResponse>>() {});

        assertThat(httpClient.requests).singleElement().satisfies(request -> assertThat(request.uri().toString())
                .isEqualTo("http://10.8.0.21:18080/api/internal/platform/opencode-runtime/management/containers/ctr_01/processes/4096/stop"));
    }

    private static RuntimeManagementBackendRoutingService service(
            RecordingHttpClient httpClient,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        return new RuntimeManagementBackendRoutingService(
                new WorkspaceServerIdentity("10.8.0.21"),
                heartbeatStore,
                new ObjectMapper().findAndRegisterModules(),
                httpClient);
    }

    private static OpencodeProcessHeartbeatStore heartbeatStore(
            List<BackendJavaProcess> backends,
            List<ManagerRuntimeSnapshot> managers) {
        return new OpencodeProcessHeartbeatStore() {
            @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) {}
            @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) {}
            @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) {}
            @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) {}
            @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() {
                return backends.stream()
                        .map(backend -> new BackendRuntimeSnapshot(
                                new LinuxServer(
                                        backend.linuxServerId(),
                                        backend.linuxServerId().value(),
                                        LinuxServerStatus.READY,
                                        Map.of(),
                                        NOW,
                                        NOW,
                                        NOW,
                                        "trace_1234567890abcdef"),
                                backend))
                        .toList();
            }
            @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return managers; }
            @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
            @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
            @Override public void cleanupExpiredHeartbeats() {}
        };
    }

    private static BackendJavaProcess backend(String backendProcessId, String linuxServerId, String listenUrl, Instant heartbeatAt) {
        return new BackendJavaProcess(
                new BackendProcessId(backendProcessId),
                new LinuxServerId(linuxServerId),
                listenUrl,
                BackendJavaProcessStatus.READY,
                NOW,
                heartbeatAt,
                NOW,
                heartbeatAt,
                "trace_1234567890abcdef");
    }

    private static ManagerRuntimeSnapshot managerSnapshot(String containerId, String linuxServerId) {
        LinuxServerId serverId = new LinuxServerId(linuxServerId);
        OpencodeContainerId parsedContainerId = new OpencodeContainerId(containerId);
        return new ManagerRuntimeSnapshot(
                new OpencodeContainer(
                        parsedContainerId,
                        serverId,
                        "opencode-" + containerId,
                        4096,
                        4105,
                        10,
                        1,
                        OpencodeContainerStatus.READY,
                        NOW,
                        NOW,
                        NOW,
                        "trace_1234567890abcdef"),
                new OpencodeContainerManager(
                        new ContainerManagerId("mgr_1234567890abcdef"),
                        parsedContainerId,
                        serverId,
                        "opencode-manager.v1",
                        ManagerConnectionStatus.CONNECTED,
                        Map.of(),
                        NOW,
                        NOW,
                        NOW,
                        "trace_1234567890abcdef"),
                List.of());
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final int status;
        private final String responseBody;
        private final List<HttpRequest> requests = new ArrayList<>();

        private RecordingHttpClient(int status, String responseBody) {
            this.status = status;
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
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            requests.add(request);
            return new StringHttpResponse<>(status, (T) responseBody, request);
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

    private record StringHttpResponse<T>(int statusCode, T body, HttpRequest request) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (left, right) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
