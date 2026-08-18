package com.enterprise.testagent.model.gateway;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModelRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.configuration.ModelProbeResult;
import io.netty.channel.ChannelOption;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

/** 使用固定非敏感样本对每项声明能力执行真实上游探测。 */
@Service
public class ModelCapabilityProbeService implements ModelCapabilityProbe {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(60);
    private static final String TINY_PNG =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    private final InternalModelProviderRepository providerRepository;
    private final InternalModelProviderModelRepository modelRepository;
    private final WebClient webClient;
    private final Clock clock;

    @Autowired
    public ModelCapabilityProbeService(
            InternalModelProviderRepository providerRepository,
            InternalModelProviderModelRepository modelRepository) {
        this(providerRepository, modelRepository, defaultWebClient(), Clock.systemUTC());
    }

    ModelCapabilityProbeService(
            InternalModelProviderRepository providerRepository,
            InternalModelProviderModelRepository modelRepository,
            WebClient webClient,
            Clock clock) {
        this.providerRepository = Objects.requireNonNull(providerRepository);
        this.modelRepository = Objects.requireNonNull(modelRepository);
        this.webClient = Objects.requireNonNull(webClient);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 探测不保存上游响应或错误文本，只落库成功与时间。 */
    @Override
    public Mono<ModelCapabilityProbeResult> probe(
            String providerId,
            String modelId,
            ModelCapability capability,
            String ucid,
            String traceId) {
        InternalModelProviderRuntimeConfig provider = requireProvider(providerId);
        InternalModelProviderModel model = modelRepository.findByModelId(modelId)
                .filter(candidate -> candidate.providerId().equals(providerId))
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "内部供应商模型不存在"));
        if (!model.enabled() || !model.declaredCapabilities().contains(capability)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "模型未启用或未声明该能力");
        }
        if (ucid == null || ucid.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "探测用户 UCID 不能为空");
        }
        ProbeRequest request = probeRequest(capability, model.upstreamModelId());
        String url = OpenAiUpstreamSupport.targetUrl(provider.provider().baseUrl(), request.path(), null);
        Mono<Boolean> call = execute(url, request, provider.authToken(), ucid, traceId)
                .timeout(PROBE_TIMEOUT)
                .onErrorReturn(false);
        return call.flatMap(succeeded -> Mono.fromCallable(() -> {
                    Instant probedAt = clock.instant();
                    modelRepository.saveProbeResult(new ModelProbeResult(
                            providerId, modelId, capability, succeeded, probedAt));
                    return new ModelCapabilityProbeResult(capability, succeeded, probedAt);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private Mono<Boolean> execute(
            String url,
            ProbeRequest request,
            String authToken,
            String ucid,
            String traceId) {
        WebClient.RequestBodySpec spec = webClient.post()
                .uri(url)
                .headers(headers -> {
                    OpenAiUpstreamSupport.applyTrustedRequestHeaders(
                            headers,
                            authToken,
                            ucid,
                            traceId,
                            request.multipart() == null ? MediaType.APPLICATION_JSON : MediaType.MULTIPART_FORM_DATA,
                            List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM));
                    if (request.embeddingInputType() != null) {
                        headers.set(ModelGatewayForwardingService.EMBEDDING_INPUT_TYPE_HEADER,
                                request.embeddingInputType());
                    }
                });
        WebClient.RequestHeadersSpec<?> headersSpec;
        if (request.multipart() != null) {
            headersSpec = spec.body(BodyInserters.fromMultipartData(request.multipart().build()));
        } else {
            headersSpec = spec.bodyValue(request.jsonBody());
        }
        return headersSpec.exchangeToMono(response ->
                response.releaseBody().thenReturn(response.statusCode().is2xxSuccessful()));
    }

    private InternalModelProviderRuntimeConfig requireProvider(String providerId) {
        return providerRepository.findEnabledRuntimeConfigs().stream()
                .filter(config -> config.provider().providerId().equals(providerId))
                .filter(config -> config.authToken() != null && !config.authToken().isBlank())
                .findFirst()
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "内部模型供应商不可用"));
    }

    private static ProbeRequest probeRequest(ModelCapability capability, String model) {
        return switch (capability) {
            case CHAT -> json("/chat/completions", Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", "health check")),
                    "max_tokens", 1,
                    "stream", false));
            case TOOLS -> json("/chat/completions", Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", "health check")),
                    "tools", List.of(Map.of(
                            "type", "function",
                            "function", Map.of(
                                    "name", "health_check",
                                    "description", "health check",
                                    "parameters", Map.of("type", "object", "properties", Map.of())))),
                    "tool_choice", "none",
                    "max_tokens", 1));
            case VISION -> json("/chat/completions", Map.of(
                    "model", model,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "text", "text", "health check"),
                                    Map.of("type", "image_url", "image_url", Map.of("url", TINY_PNG))))),
                    "max_tokens", 1));
            case REASONING -> json("/chat/completions", Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", "1+1")),
                    "reasoning_effort", "low",
                    "max_tokens", 1));
            case EMBEDDING -> embedding(model);
            case RERANK -> json("/rerank", Map.of(
                    "model", model,
                    "query", "health check",
                    "documents", List.of("health check"),
                    "top_n", 1));
            case IMAGE -> json("/images/generations", Map.of(
                    "model", model,
                    "prompt", "one black pixel",
                    "n", 1));
            case SPEECH -> json("/audio/speech", Map.of(
                    "model", model,
                    "voice", "alloy",
                    "input", "health check"));
            case TRANSCRIPTION -> transcription(model);
        };
    }

    private static ProbeRequest json(String path, Object body) {
        return new ProbeRequest(path, body, null, null);
    }

    /** Embedding 探测固定使用查询向量语义，兼容要求显式输入类型的内部 CPU BGE。 */
    private static ProbeRequest embedding(String model) {
        return new ProbeRequest(
                "/embeddings",
                Map.of("model", model, "input", "health check"),
                null,
                "query");
    }

    private static ProbeRequest transcription(String model) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("model", model);
        builder.part("file", new ByteArrayResource(silentWav()) {
            @Override
            public String getFilename() {
                return "health-check.wav";
            }
        }).contentType(MediaType.parseMediaType("audio/wav"));
        return new ProbeRequest("/audio/transcriptions", null, builder, null);
    }

    /** 生成 100ms/8kHz/16-bit 单声道静音 WAV，避免探测依赖外部文件。 */
    private static byte[] silentWav() {
        int dataLength = 1600;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataLength);
        buffer.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(16).putShort((short) 1).putShort((short) 1);
        buffer.putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16);
        buffer.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(dataLength);
        buffer.put(new byte[dataLength]);
        return buffer.array();
    }

    private static WebClient defaultWebClient() {
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .responseTimeout(PROBE_TIMEOUT);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(client))
                .build();
    }

    private record ProbeRequest(
            String path,
            Object jsonBody,
            MultipartBodyBuilder multipart,
            String embeddingInputType) {
    }
}
