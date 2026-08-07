package com.enterprise.testagent.opencode.runtime.internalmodel.observability;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModelRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecord;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordRepository;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallSource;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatus;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatusRepository;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProviderRegistry;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProviderSnapshot;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 对每个启用的内部模型 provider 发最小 chat 探测，验证当前 Java 进程内存快照中的
 * 端点与 Token 能否连通。结果落观测明细表（source=PROBE）并维护逐 provider 探活状态。
 *
 * <p>探活只发送 max_tokens=1 的最小请求，不携带任何业务输入；失败分类复用
 * {@link InternalModelCallOutcomeClassifier}，只记录结构化字段。</p>
 */
@Service
public class InternalModelProviderProbeService {

    private static final Logger log = LoggerFactory.getLogger(InternalModelProviderProbeService.class);

    /** 探活记录使用的固定调用方标识，不关联真实用户。 */
    public static final String PROBE_UCID = "platform-probe";
    private static final String UCID_HEADER = "ucid";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);
    private static final String CHAT_PATH = "/chat/completions";

    private final InternalModelProviderRegistry registry;
    private final InternalModelProviderModelRepository modelRepository;
    private final InternalModelCallRecordRepository callRecordRepository;
    private final InternalModelProbeStatusRepository probeStatusRepository;

    public InternalModelProviderProbeService(
            InternalModelProviderRegistry registry,
            InternalModelProviderModelRepository modelRepository,
            InternalModelCallRecordRepository callRecordRepository,
            InternalModelProbeStatusRepository probeStatusRepository) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.modelRepository = Objects.requireNonNull(modelRepository, "modelRepository must not be null");
        this.callRecordRepository =
                Objects.requireNonNull(callRecordRepository, "callRecordRepository must not be null");
        this.probeStatusRepository =
                Objects.requireNonNull(probeStatusRepository, "probeStatusRepository must not be null");
    }

    /** 探活全部启用的 provider，返回逐 provider 结果；单点失败不影响其他 provider。 */
    public Result probeAll(String traceId) {
        InternalModelProviderSnapshot snapshot = registry.currentSnapshot();
        Result result = new Result();
        for (InternalModelProvider provider : snapshot.providers()) {
            result.put(provider.providerId(), probeProvider(provider, traceId));
        }
        return result;
    }

    /** 手动触发单个 provider 探活；provider 不在快照中时抛统一错误。 */
    public Result probeProvider(String providerId, String traceId) {
        InternalModelProvider provider = registry.requireProvider(providerId);
        return new Result().put(providerId, probeProvider(provider, traceId));
    }

    private ProbeOutcome probeProvider(InternalModelProvider provider, String traceId) {
        String model = firstEnabledModelId(provider.providerId());
        if (model == null) {
            // provider 未启用、Token 未配置或无可用模型均视为暂不可用，只更新状态不落明细。
            recordStatus(provider.providerId(), InternalModelCallOutcome.PROVIDER_UNAVAILABLE,
                    null, null, traceId);
            return ProbeOutcome.unavailable(provider.providerId());
        }
        return sendProbe(provider, model, traceId);
    }

    private String firstEnabledModelId(String providerId) {
        return modelRepository.findByProviderId(providerId).stream()
                .filter(InternalModelProviderModel::enabled)
                .map(InternalModelProviderModel::upstreamModelId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private ProbeOutcome sendProbe(InternalModelProvider provider, String model, String traceId) {
        InternalModelProviderRuntimeConfig runtimeConfig;
        try {
            runtimeConfig = registry.requireRuntimeConfig(provider.providerId());
        } catch (PlatformException exception) {
            recordStatus(provider.providerId(), InternalModelCallOutcome.PROVIDER_UNAVAILABLE,
                    null, exception.getClass().getSimpleName(), traceId);
            return ProbeOutcome.unavailable(provider.providerId());
        }
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        WebClient client = webClient();
        try {
            HttpStatusCode status = client.post()
                    .uri(normalizedTarget(provider.baseUrl(), CHAT_PATH))
                    .headers(headers -> applyHeaders(headers, runtimeConfig.authToken(), traceId))
                    .bodyValue(probeBody(model))
                    .retrieve()
                    .toBodilessEntity()
                    .map(entity -> entity.getStatusCode())
                    .block(RESPONSE_TIMEOUT);
            if (status == null) {
                return recordProbe(provider.providerId(), model, InternalModelCallOutcome.UPSTREAM_FIRST_RESPONSE_TIMEOUT,
                        null, null, startedAt, startedNanos, null, traceId);
            }
            InternalModelCallOutcome outcome = status.is2xxSuccessful()
                    ? InternalModelCallOutcome.SUCCESS
                    : InternalModelCallOutcome.UPSTREAM_HTTP_ERROR;
            return recordProbe(provider.providerId(), model, outcome, status.value(), null,
                    startedAt, startedNanos, startedNanos, traceId);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException httpError) {
            // retrieve() 对非 2xx 直接抛异常：按真实上游状态码归为 HTTP_ERROR。
            HttpStatusCode status = httpError.getStatusCode();
            InternalModelCallOutcome outcome = status.is2xxSuccessful()
                    ? InternalModelCallOutcome.SUCCESS
                    : InternalModelCallOutcome.UPSTREAM_HTTP_ERROR;
            return recordProbe(provider.providerId(), model, outcome, status.value(), null,
                    startedAt, startedNanos, startedNanos, traceId);
        } catch (RuntimeException error) {
            InternalModelCallOutcome outcome = InternalModelCallOutcomeClassifier.classify(
                    error, new InternalModelCallOutcomeClassifier.TimeoutSignals(false, false, false));
            return recordProbe(provider.providerId(), model, outcome, null,
                    InternalModelCallOutcomeClassifier.errorClass(error),
                    startedAt, startedNanos, null, traceId);
        }
    }

    private ProbeOutcome recordProbe(
            String providerId,
            String model,
            InternalModelCallOutcome outcome,
            Integer httpStatus,
            String errorClass,
            Instant startedAt,
            long startedNanos,
            Long firstByteNanos,
            String traceId) {
        long durationMillis = (System.nanoTime() - startedNanos) / 1_000_000;
        Long firstByteMillis = firstByteNanos == null ? null : (firstByteNanos - startedNanos) / 1_000_000;
        InternalModelCallRecord record = new InternalModelCallRecord(
                null, providerId, model, CHAT_PATH, InternalModelCallSource.PROBE, outcome,
                httpStatus, errorClass, false, durationMillis, firstByteMillis,
                traceId == null ? "" : traceId, PROBE_UCID, startedAt);
        try {
            callRecordRepository.record(record);
        } catch (RuntimeException ignored) {
            log.warn("probe detail record failed, providerId={}", providerId);
        }
        recordStatus(providerId, outcome, httpStatus, errorClass, traceId);
        return ProbeOutcome.of(providerId, outcome, httpStatus, durationMillis);
    }

    @Transactional
    void recordStatus(
            String providerId,
            InternalModelCallOutcome outcome,
            Integer httpStatus,
            String errorClass,
            String traceId) {
        Instant now = Instant.now();
        // 连续失败计数由 SQL 依据 outcome 自动递增/归零，写入方不传递。
        probeStatusRepository.upsert(new InternalModelProbeStatus(
                providerId, outcome, httpStatus, errorClass, null, now,
                outcome == InternalModelCallOutcome.SUCCESS ? now : null,
                0, traceId == null ? "" : traceId));
    }

    private Map<String, Object> probeBody(String model) {
        return Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", "health check")),
                "max_tokens", 1,
                "stream", false);
    }

    private void applyHeaders(HttpHeaders headers, String authToken, String traceId) {
        headers.setBearerAuth(authToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(UCID_HEADER, PROBE_UCID);
        if (traceId != null && !traceId.isBlank()) {
            headers.set("X-Trace-Id", traceId);
        }
    }

    private String normalizedTarget(String baseUrl, String path) {
        String normalizedBase = baseUrl;
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .responseTimeout(RESPONSE_TIMEOUT);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /** 单次探活结果集合：providerId -> outcome 等结构化字段。 */
    public static final class Result {
        private final Map<String, ProbeOutcome> outcomes = new LinkedHashMap<>();

        Result put(String providerId, ProbeOutcome outcome) {
            outcomes.put(providerId, outcome);
            return this;
        }

        public boolean allSucceeded() {
            return !outcomes.isEmpty() && outcomes.values().stream().allMatch(ProbeOutcome::succeeded);
        }

        public Map<String, ProbeOutcome> outcomes() {
            return outcomes;
        }
    }

    /** 单个 provider 的探活结果。 */
    public record ProbeOutcome(
            String providerId,
            InternalModelCallOutcome outcome,
            Integer httpStatus,
            long durationMillis) {

        static ProbeOutcome of(
                String providerId, InternalModelCallOutcome outcome, Integer httpStatus, long durationMillis) {
            return new ProbeOutcome(providerId, outcome, httpStatus, durationMillis);
        }

        static ProbeOutcome unavailable(String providerId) {
            return new ProbeOutcome(providerId, InternalModelCallOutcome.PROVIDER_UNAVAILABLE, null, 0);
        }

        boolean succeeded() {
            return outcome == InternalModelCallOutcome.SUCCESS;
        }
    }
}
