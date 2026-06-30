package com.icbc.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.observability.TraceConstants;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class BackendHttpForwarderTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");

    @Test
    void forwardsRawRequestWithAuthTraceQueryBodyAndRoutedHeader() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"success":true,"traceId":"trace_1234567890abcdef","data":{"status":"READY"}}
                """, true);
        BackendHttpForwarder forwarder = new BackendHttpForwarder(new ObjectMapper().findAndRegisterModules(), httpClient);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/platform/configuration-management/applications/app_1/workspaces?force=true")
                .header(TraceConstants.TRACE_ID_HEADER, "trace_1234567890abcdef")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"workspaceName\":\"Demo\"}"));

        forwarder.forwardRaw(exchange, backend("10.8.0.22", "http://10.8.0.22:18080"))
                .block(Duration.ofSeconds(2));

        assertThat(httpClient.requests).singleElement().satisfies(request -> {
            assertThat(request.uri().toString()).isEqualTo(
                    "http://10.8.0.22:18080/api/internal/platform/configuration-management/applications/app_1/workspaces?force=true");
            assertThat(request.headers().firstValue(TraceConstants.TRACE_ID_HEADER)).contains("trace_1234567890abcdef");
            assertThat(request.headers().firstValue(org.springframework.http.HttpHeaders.AUTHORIZATION)).contains("Bearer user-token");
            assertThat(request.headers().firstValue(BackendHttpForwarder.ROUTED_HEADER)).contains("true");
        });
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"status\":\"READY\"");
    }

    @Test
    void forwardsTypedRequestAndParsesApiResponse() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"success":true,"traceId":"trace_typed","data":{"value":"ok"}}
                """, false);
        BackendHttpForwarder forwarder = new BackendHttpForwarder(new ObjectMapper().findAndRegisterModules(), httpClient);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/platform/workspace-management/agent-config/public/stage")
                .header(TraceConstants.TRACE_ID_HEADER, "trace_typed")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .build());

        ApiResponse<Map<String, String>> response = forwarder.forwardTyped(
                exchange,
                backend("10.8.0.22", "http://10.8.0.22:18080"),
                Map.of("files", List.of("a.md")),
                new com.fasterxml.jackson.core.type.TypeReference<>() {});

        assertThat(response.data()).containsEntry("value", "ok");
        assertThat(httpClient.requests).singleElement().satisfies(request -> {
            assertThat(request.headers().firstValue(BackendHttpForwarder.ROUTED_HEADER)).contains("true");
            assertThat(request.headers().firstValue(org.springframework.http.HttpHeaders.CONTENT_TYPE)).contains("application/json");
        });
    }

    private static BackendJavaProcess backend(String linuxServerId, String listenUrl) {
        return new BackendJavaProcess(
                new BackendProcessId("bjp_1234567890abcdef"),
                new LinuxServerId(linuxServerId),
                listenUrl,
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                "trace_backend");
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final int status;
        private final String responseBody;
        private final boolean byteArrayResponse;
        private final List<HttpRequest> requests = new ArrayList<>();

        private RecordingHttpClient(int status, String responseBody, boolean byteArrayResponse) {
            this.status = status;
            this.responseBody = responseBody;
            this.byteArrayResponse = byteArrayResponse;
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
            if (byteArrayResponse) {
                return (HttpResponse<T>) new ByteArrayHttpResponse(status, responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8), request);
            }
            return new StringHttpResponse<>((T) responseBody, request, status);
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record StringHttpResponse<T>(T body, HttpRequest request, int statusCode) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(org.springframework.http.HttpHeaders.CONTENT_TYPE, List.of("application/json")), (left, right) -> true);
        }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private record ByteArrayHttpResponse(int statusCode, byte[] body, HttpRequest request) implements HttpResponse<byte[]> {
        @Override public Optional<HttpResponse<byte[]>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(org.springframework.http.HttpHeaders.CONTENT_TYPE, List.of("application/json")), (left, right) -> true);
        }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
