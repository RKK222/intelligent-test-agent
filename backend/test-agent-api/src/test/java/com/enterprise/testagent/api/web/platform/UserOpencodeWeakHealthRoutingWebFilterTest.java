package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class UserOpencodeWeakHealthRoutingWebFilterTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");

    @Test
    void forwardsRemoteWeakHealthToBackendFromQueryLinuxServer() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"success":true,"traceId":"trace_1234567890abcdef","data":{"healthy":true,"status":"HEALTHY","serviceStatus":"RUNNING","linuxServerId":"server-b","containerId":"ctr_01","port":4096,"baseUrl":"http://server-b:4096","message":"ok"}}
                """);
        UserOpencodeWeakHealthRoutingWebFilter filter = filter(httpClient, heartbeatStore(List.of(
                backend("bjp_1234567890abcdef", "server-b", "http://10.8.0.22:8080", NOW))));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/processes/me/health?linuxServerId=server-b&containerId=ctr_01&port=4096")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isFalse();
        assertThat(httpClient.requests).singleElement().satisfies(request -> {
            assertThat(request.uri().toString()).isEqualTo(
                    "http://10.8.0.22:8080/api/internal/agent/opencode/processes/me/health?linuxServerId=server-b&containerId=ctr_01&port=4096");
            assertThat(request.headers().firstValue("X-Trace-Id")).contains("trace_1234567890abcdef");
            assertThat(request.headers().firstValue(org.springframework.http.HttpHeaders.AUTHORIZATION)).contains("Bearer user-token");
            assertThat(request.headers().firstValue(UserOpencodeBackendRoutingWebFilter.ROUTED_HEADER)).contains("true");
        });
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(200);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"healthy\":true", "\"status\":\"HEALTHY\"");
    }

    @Test
    void missingRemoteBackendReturnsUnhealthyResponseWithoutCallingController() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeWeakHealthRoutingWebFilter filter = filter(httpClient, heartbeatStore(List.of()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/processes/me/health?linuxServerId=server-b&containerId=ctr_01&port=4096")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isFalse();
        assertThat(httpClient.requests).isEmpty();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(200);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains(
                "\"healthy\":false",
                "\"status\":\"BACKEND_UNAVAILABLE\"",
                "\"linuxServerId\":\"server-b\"",
                "\"containerId\":\"ctr_01\"",
                "\"port\":4096");
    }

    @Test
    void routedHeaderForRemoteTargetReturnsUnhealthyResponseToAvoidLoop() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeWeakHealthRoutingWebFilter filter = filter(httpClient, heartbeatStore(List.of(
                backend("bjp_1234567890abcdef", "server-b", "http://10.8.0.22:8080", NOW))));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/processes/me/health?linuxServerId=server-b&containerId=ctr_01&port=4096")
                .header(UserOpencodeBackendRoutingWebFilter.ROUTED_HEADER, "true")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isFalse();
        assertThat(httpClient.requests).isEmpty();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(200);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains(
                "\"healthy\":false",
                "\"status\":\"BACKEND_UNAVAILABLE\"");
    }

    @Test
    void currentServerWeakHealthContinuesToController() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{}");
        UserOpencodeWeakHealthRoutingWebFilter filter = filter(httpClient, heartbeatStore(List.of()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/processes/me/health?linuxServerId=server-a&containerId=ctr_01&port=4096")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        assertThat(httpClient.requests).isEmpty();
    }

    private static UserOpencodeWeakHealthRoutingWebFilter filter(
            RecordingHttpClient httpClient,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        return new UserOpencodeWeakHealthRoutingWebFilter(
                new WorkspaceServerIdentity("server-a"),
                heartbeatStore,
                new ObjectMapper().findAndRegisterModules(),
                httpClient);
    }

    private static WebFilterChain chain(java.util.function.Function<org.springframework.web.server.ServerWebExchange, Mono<Void>> delegate) {
        return delegate::apply;
    }

    private static OpencodeProcessHeartbeatStore heartbeatStore(List<BackendJavaProcess> backends) {
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
                                        backend.lastHeartbeatAt(),
                                        backend.createdAt(),
                                        backend.updatedAt(),
                                        "trace_backend"),
                                backend))
                        .toList();
            }
            @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
            @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
            @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
            @Override public void cleanupExpiredHeartbeats() {}
        };
    }

    private static BackendJavaProcess backend(String processId, String linuxServerId, String listenUrl, Instant heartbeatAt) {
        return new BackendJavaProcess(
                new BackendProcessId(processId),
                new LinuxServerId(linuxServerId),
                listenUrl,
                BackendJavaProcessStatus.READY,
                NOW.minusSeconds(60),
                heartbeatAt,
                NOW.minusSeconds(60),
                heartbeatAt,
                "trace_backend");
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
            return new BytesResponse<>((T) responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8), request, status);
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
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

    private record BytesResponse<T>(T body, HttpRequest request, int statusCode) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("content-type", List.of("application/json")), (left, right) -> true);
        }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
