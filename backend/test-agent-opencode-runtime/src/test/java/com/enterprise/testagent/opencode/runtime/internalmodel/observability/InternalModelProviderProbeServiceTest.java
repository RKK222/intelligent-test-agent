package com.enterprise.testagent.opencode.runtime.internalmodel.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 覆盖探活成功、上游非 2xx 与连接失败三类结果，并验证明细与状态落库。 */
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
    void probeSucceedsAndRecordsDetailAndStatus() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/chat/completions", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
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

        assertThat(result.allSucceeded()).isTrue();
        var outcome = result.outcomes().get(PROVIDER_ID);
        assertThat(outcome.outcome()).isEqualTo(InternalModelCallOutcome.SUCCESS);
        assertThat(outcome.httpStatus()).isEqualTo(200);
        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().source()).isEqualTo(InternalModelCallSource.PROBE);
        assertThat(recorded.getFirst().outcome()).isEqualTo(InternalModelCallOutcome.SUCCESS);
        assertThat(statuses).hasSize(1);
        assertThat(statuses.getFirst().consecutiveFailures()).isZero();
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

    private InternalModelProviderProbeService service(
            String baseUrl,
            List<InternalModelCallRecord> recorded,
            List<InternalModelProbeStatus> statuses) {
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
                registry, modelRepository, recordRepository, statusRepository);
    }

    private String upstreamBaseUrl() {
        return "http://127.0.0.1:" + upstream.getAddress().getPort();
    }
}
