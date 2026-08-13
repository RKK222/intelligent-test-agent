package com.enterprise.testagent.opencode.runtime.internalmodel.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModelRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecord;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordRepository;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallSource;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatus;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatusRepository;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProviderRegistry;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 覆盖真实 SSE 探活成功、协议异常、超时、上游非 2xx 与连接失败，并验证观测落库。 */
class InternalModelProviderProbeServiceTest {

    private static final String PROVIDER_ID = "enterprise-deepseek";
    private static final String MODEL_TOKEN = "model-token";

    private HttpServer upstream;

    @AfterEach
    void tearDown() {
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    @Test
    void springSelectsProductionConstructorWhenTestConstructorAlsoExists() {
        new ApplicationContextRunner()
                .withBean(InternalModelProviderRegistry.class, () -> mock(InternalModelProviderRegistry.class))
                .withBean(InternalModelProviderModelRepository.class,
                        () -> mock(InternalModelProviderModelRepository.class))
                .withBean(InternalModelCallRecordRepository.class,
                        () -> mock(InternalModelCallRecordRepository.class))
                .withBean(InternalModelProbeStatusRepository.class,
                        () -> mock(InternalModelProbeStatusRepository.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(InternalModelProviderProbeService.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(InternalModelProviderProbeService.class));
    }

    @Test
    void probeSucceedsAndRecordsDetailAndStatus() throws IOException {
        AtomicReference<String> authTokenHeader = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/chat/completions", exchange -> {
            authTokenHeader.set(exchange.getRequestHeaders().getFirst("Auth-Token"));
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                writeAndFlush(output, "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n");
                pause(30);
                writeAndFlush(output, "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\n");
                pause(30);
                writeAndFlush(output, "data: [DONE]\n\n");
            }
        });
        upstream.start();

        List<InternalModelCallRecord> recorded = new CopyOnWriteArrayList<>();
        List<InternalModelProbeStatus> statuses = new CopyOnWriteArrayList<>();
        InternalModelProviderProbeService service = service(
                upstreamBaseUrl(), recorded, statuses);

        var result = service.probeAll("trace_probe");

        assertThat(result.allSucceeded()).isTrue();
        assertThat(authTokenHeader.get()).isEqualTo(MODEL_TOKEN);
        assertThat(authorizationHeader.get()).isNull();
        var outcome = result.outcomes().get(PROVIDER_ID);
        assertThat(outcome.outcome()).isEqualTo(InternalModelCallOutcome.SUCCESS);
        assertThat(outcome.httpStatus()).isEqualTo(200);
        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().source()).isEqualTo(InternalModelCallSource.PROBE);
        assertThat(recorded.getFirst().outcome()).isEqualTo(InternalModelCallOutcome.SUCCESS);
        // 首字节、首 token 与 [DONE] 分阶段取样，且均应早于端到端耗时。
        assertThat(recorded.getFirst().streaming()).isTrue();
        assertThat(recorded.getFirst().firstByteMillis()).isNotNull();
        assertThat(recorded.getFirst().firstByteMillis()).isGreaterThanOrEqualTo(0);
        assertThat(recorded.getFirst().firstByteMillis()).isLessThan(recorded.getFirst().durationMillis());
        assertThat(recorded.getFirst().firstTokenMillis()).isGreaterThanOrEqualTo(
                recorded.getFirst().firstByteMillis());
        assertThat(recorded.getFirst().streamCompleteMillis()).isGreaterThanOrEqualTo(
                recorded.getFirst().firstTokenMillis());
        assertThat(recorded.getFirst().streamCompleteMillis()).isLessThanOrEqualTo(
                recorded.getFirst().durationMillis());
        assertThat(statuses).hasSize(1);
        assertThat(statuses.getFirst().consecutiveFailures()).isZero();
        assertThat(statuses.getFirst().lastDurationMillis())
                .isEqualTo(recorded.getFirst().durationMillis());
    }

    @Test
    void probeAcceptsFinishReasonThenEofWithoutDoneMarker() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                writeAndFlush(output,
                        "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":null}]}\n\n");
                writeAndFlush(output,
                        "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n");
            }
        });
        upstream.start();

        List<InternalModelCallRecord> recorded = new CopyOnWriteArrayList<>();
        List<InternalModelProbeStatus> statuses = new CopyOnWriteArrayList<>();

        var result = service(upstreamBaseUrl(), recorded, statuses).probeAll("trace_finish_reason");

        assertThat(result.outcomes().get(PROVIDER_ID).outcome())
                .isEqualTo(InternalModelCallOutcome.SUCCESS);
        assertThat(recorded.getFirst().outcome()).isEqualTo(InternalModelCallOutcome.SUCCESS);
        assertThat(recorded.getFirst().firstTokenMillis()).isNotNull();
        assertThat(recorded.getFirst().streamCompleteMillis())
                .isGreaterThanOrEqualTo(recorded.getFirst().firstTokenMillis());
        assertThat(statuses.getFirst().lastSuccessAt()).isNotNull();
    }

    @Test
    void probeRecordsHttpErrorStatus() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/chat/completions", exchange -> {
            byte[] body = "bad".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        upstream.start();

        List<InternalModelCallRecord> recorded = new CopyOnWriteArrayList<>();
        List<InternalModelProbeStatus> statuses = new CopyOnWriteArrayList<>();
        InternalModelProviderProbeService service = service(
                upstreamBaseUrl(), recorded, statuses);

        var result = service.probeAll("trace_probe");

        var outcome = result.outcomes().get(PROVIDER_ID);
        assertThat(outcome.outcome()).isEqualTo(InternalModelCallOutcome.UPSTREAM_HTTP_ERROR);
        assertThat(outcome.httpStatus()).isEqualTo(500);
        assertThat(recorded.getFirst().outcome()).isEqualTo(InternalModelCallOutcome.UPSTREAM_HTTP_ERROR);
        // 状态记录：失败后连续失败计数由 SQL 递增；本测试 mock 不做计数，仅验证字段。
        assertThat(statuses.getFirst().lastOutcome()).isEqualTo(InternalModelCallOutcome.UPSTREAM_HTTP_ERROR);
    }

    @Test
    void probeRecordsConnectFailureWhenUpstreamUnreachable() {
        // 不启动 HttpServer，端口不可达。
        List<InternalModelCallRecord> recorded = new CopyOnWriteArrayList<>();
        List<InternalModelProbeStatus> statuses = new CopyOnWriteArrayList<>();
        InternalModelProviderProbeService service = service(
                "http://127.0.0.1:1", recorded, statuses);

        var result = service.probeAll("trace_probe");

        var outcome = result.outcomes().get(PROVIDER_ID);
        assertThat(outcome.outcome()).isEqualTo(InternalModelCallOutcome.UPSTREAM_CONNECT_FAILED);
        assertThat(recorded.getFirst().outcome()).isEqualTo(InternalModelCallOutcome.UPSTREAM_CONNECT_FAILED);
    }

    @Test
    void probeKeepsResponseHeadersWhenBodyTimesOut() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                writeAndFlush(output, ": headers-ready\n\n");
                pause(300);
            }
        });
        upstream.start();

        List<InternalModelCallRecord> recorded = new CopyOnWriteArrayList<>();
        List<InternalModelProbeStatus> statuses = new CopyOnWriteArrayList<>();
        InternalModelProviderProbeService service = service(
                upstreamBaseUrl(), recorded, statuses, Duration.ofSeconds(1), Duration.ofMillis(80));

        var result = service.probeAll("trace_probe_body_timeout");

        assertThat(result.outcomes().get(PROVIDER_ID).outcome())
                .isEqualTo(InternalModelCallOutcome.UPSTREAM_FIRST_EVENT_TIMEOUT);
        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().httpStatus()).isEqualTo(200);
        assertThat(recorded.getFirst().firstByteMillis()).isNotNull();
        assertThat(recorded.getFirst().firstTokenMillis()).isNull();
        assertThat(recorded.getFirst().streamCompleteMillis()).isNull();
    }

    @Test
    void probeRejectsEmptyNonStreamingSuccessBody() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        upstream.start();

        List<InternalModelCallRecord> recorded = new CopyOnWriteArrayList<>();
        List<InternalModelProbeStatus> statuses = new CopyOnWriteArrayList<>();

        var result = service(upstreamBaseUrl(), recorded, statuses).probeAll("trace_empty_200");

        assertThat(result.outcomes().get(PROVIDER_ID).outcome())
                .isEqualTo(InternalModelCallOutcome.UPSTREAM_STREAM_INTERRUPTED);
        assertThat(recorded.getFirst().streaming()).isFalse();
        assertThat(recorded.getFirst().firstTokenMillis()).isNull();
    }

    @Test
    void probeDoesNotLetCommentsExtendIdleDeadline() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                writeAndFlush(output, "data: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\n");
                for (int index = 0; index < 10; index++) {
                    pause(20);
                    writeAndFlush(output, ": keepalive\n\n");
                }
            } catch (IOException ignored) {
                // 客户端在输出空闲超时后主动关闭连接属于预期行为。
            }
        });
        upstream.start();

        List<InternalModelCallRecord> recorded = new CopyOnWriteArrayList<>();
        List<InternalModelProbeStatus> statuses = new CopyOnWriteArrayList<>();
        var result = service(
                upstreamBaseUrl(), recorded, statuses,
                Duration.ofSeconds(1), Duration.ofMillis(80))
                .probeAll("trace_idle_timeout");

        assertThat(result.outcomes().get(PROVIDER_ID).outcome())
                .isEqualTo(InternalModelCallOutcome.UPSTREAM_STREAM_IDLE_TIMEOUT);
        assertThat(recorded.getFirst().firstTokenMillis()).isNotNull();
        assertThat(recorded.getFirst().streamCompleteMillis()).isNull();
    }

    private InternalModelProviderProbeService service(
            String baseUrl,
            List<InternalModelCallRecord> recorded,
            List<InternalModelProbeStatus> statuses) {
        return service(baseUrl, recorded, statuses, Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    private InternalModelProviderProbeService service(
            String baseUrl,
            List<InternalModelCallRecord> recorded,
            List<InternalModelProbeStatus> statuses,
            Duration connectTimeout,
            Duration responseTimeout) {
        InternalModelProvider provider = new InternalModelProvider(
                PROVIDER_ID, PROVIDER_ID, baseUrl, true, 1,
                Instant.now(), Instant.now());
        InternalModelProviderRepository providerRepository = mock(InternalModelProviderRepository.class);
        when(providerRepository.findEnabledRuntimeConfigs())
                .thenReturn(List.of(new InternalModelProviderRuntimeConfig(provider, MODEL_TOKEN)));
        InternalModelProviderRegistry registry = new InternalModelProviderRegistry(providerRepository);
        registry.refresh("trace_setup", "test");

        InternalModelProviderModel model = new InternalModelProviderModel(
                PROVIDER_ID, "m1", "DeepSeek-V4", "DeepSeek", 128000L, true,
                java.util.Set.of(), java.util.Set.of(), null, Instant.now(), Instant.now());
        InternalModelProviderModelRepository modelRepository = mock(InternalModelProviderModelRepository.class);
        when(modelRepository.findByProviderId(PROVIDER_ID)).thenReturn(List.of(model));

        InternalModelCallRecordRepository recordRepository = mock(InternalModelCallRecordRepository.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            recorded.add(invocation.getArgument(0));
            return null;
        }).when(recordRepository).record(org.mockito.ArgumentMatchers.any(InternalModelCallRecord.class));

        InternalModelProbeStatusRepository statusRepository = mock(InternalModelProbeStatusRepository.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            statuses.add(invocation.getArgument(0));
            return null;
        }).when(statusRepository).upsert(org.mockito.ArgumentMatchers.any(InternalModelProbeStatus.class));

        return new InternalModelProviderProbeService(
                registry, modelRepository, recordRepository, statusRepository,
                new ObjectMapper(), connectTimeout, responseTimeout);
    }

    private String upstreamBaseUrl() {
        return "http://127.0.0.1:" + upstream.getAddress().getPort();
    }

    private static void writeAndFlush(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
