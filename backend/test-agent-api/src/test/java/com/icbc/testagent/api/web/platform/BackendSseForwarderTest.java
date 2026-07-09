package com.icbc.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.observability.TraceConstants;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;

class BackendSseForwarderTest {

    private static final Instant NOW = Instant.parse("2026-07-09T00:00:00Z");
    private static final String RUN_ID = "run_1234567890abcdef";

    @Test
    void streamsSseResponseAndPreservesAuthTraceLastEventIdAndQuery() throws Exception {
        AtomicReference<Headers> receivedHeaders = new AtomicReference<>();
        AtomicReference<String> receivedRequestUri = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/agent/opencode/runs/" + RUN_ID + "/events", exchange -> {
            receivedHeaders.set(exchange.getRequestHeaders());
            receivedRequestUri.set(exchange.getRequestURI().toString());
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE);
            exchange.getResponseHeaders().add(TraceConstants.TRACE_ID_HEADER, "trace_target");
            exchange.sendResponseHeaders(200, 0);
            writeSseBody(exchange.getResponseBody());
        });
        server.start();
        try {
            BackendSseForwarder forwarder = new BackendSseForwarder(WebClient.builder().build());
            int port = server.getAddress().getPort();
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                    .get("/api/internal/agent/opencode/runs/" + RUN_ID + "/events?lastEventId=12")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .header(TraceConstants.TRACE_ID_HEADER, "trace_1234567890abcdef")
                    .header("Last-Event-ID", "11")
                    .build());

            forwarder.forward(exchange, backend("http://127.0.0.1:" + port)).block(Duration.ofSeconds(3));

            assertThat(receivedRequestUri.get()).isEqualTo(
                    "/api/internal/agent/opencode/runs/" + RUN_ID + "/events?lastEventId=12");
            assertThat(receivedHeaders.get().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer user-token");
            assertThat(receivedHeaders.get().getFirst(TraceConstants.TRACE_ID_HEADER))
                    .isEqualTo("trace_1234567890abcdef");
            assertThat(receivedHeaders.get().getFirst("Last-Event-ID")).isEqualTo("11");
            assertThat(receivedHeaders.get().getFirst(BackendHttpForwarder.ROUTED_HEADER)).isEqualTo("true");
            assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(200);
            assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
            assertThat(exchange.getResponse().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER))
                    .isEqualTo("trace_target");
            assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(1)))
                    .contains("event: run.started", "\"type\":\"run.started\"");
        } finally {
            server.stop(0);
        }
    }

    private static void writeSseBody(OutputStream responseBody) throws IOException {
        try (OutputStream body = responseBody) {
            body.write("event: run.started\n".getBytes(StandardCharsets.UTF_8));
            body.write("data: {\"type\":\"run.started\"}\n\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static BackendJavaProcess backend(String listenUrl) {
        return new BackendJavaProcess(
                new BackendProcessId("bjp_1234567890abcdef"),
                new LinuxServerId("server-b"),
                listenUrl,
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                "trace_backend");
    }
}
