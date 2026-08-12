package com.enterprise.testagent.model.gateway;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.modelgateway.ModelGatewayUsageDailyRepository;
import com.enterprise.testagent.domain.modelgateway.ModelGatewayUsageDelta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelOption;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

/**
 * 企业模型网关的 OpenAI-compatible 转发核心。
 *
 * <p>该服务只接受目录中的公开模型 ID；供应商、密钥及 UCID 均来自服务端可信状态。响应内容仅在内存中
 * 短暂截取有限字节用于提取 token 聚合，绝不进入日志或逐请求存储。</p>
 */
@Service
public class ModelGatewayForwardingService implements ModelGatewayForwarder {

    public static final String GATEWAY_PATH = "/api/internal/platform/model-gateway/v1";
    public static final String PROVIDER_HEADER = OpenAiUpstreamSupport.PROVIDER_HEADER;
    public static final String UCID_HEADER = OpenAiUpstreamSupport.UCID_HEADER;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration FIRST_RESPONSE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration FIRST_EVENT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_USAGE_CAPTURE_BYTES = 1024 * 1024;
    private static final long MAX_MULTIPART_PART_BYTES = 100L * 1024L * 1024L;
    private static final Map<String, ModelCapability> ENDPOINT_CAPABILITIES = Map.of(
            "/chat/completions", ModelCapability.CHAT,
            "/responses", ModelCapability.CHAT,
            "/embeddings", ModelCapability.EMBEDDING,
            "/rerank", ModelCapability.RERANK,
            "/images/generations", ModelCapability.IMAGE,
            "/audio/speech", ModelCapability.SPEECH);

    private final ModelGatewayCatalogService catalogService;
    private final ModelGatewayUsageDailyRepository usageRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ModelGatewayForwardingService(
            ModelGatewayCatalogService catalogService,
            ModelGatewayUsageDailyRepository usageRepository,
            ObjectMapper objectMapper) {
        this(catalogService, usageRepository, defaultWebClient(), objectMapper, Clock.systemUTC());
    }

    ModelGatewayForwardingService(
            ModelGatewayCatalogService catalogService,
            ModelGatewayUsageDailyRepository usageRepository,
            WebClient webClient,
            ObjectMapper objectMapper,
            Clock clock) {
        this.catalogService = Objects.requireNonNull(catalogService);
        this.usageRepository = Objects.requireNonNull(usageRepository);
        this.webClient = Objects.requireNonNull(webClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 校验固定端点、模型 ID 和附加能力，并将公开模型改写为供应商模型 ID。 */
    @Override
    public PreparedModelGatewayRequest prepare(ServerWebExchange exchange, byte[] requestBody) {
        String endpoint = endpoint(exchange);
        ModelCapability primaryCapability = ENDPOINT_CAPABILITIES.get(endpoint);
        if (primaryCapability == null) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "企业模型网关端点不存在");
        }
        ObjectNode body = parseObject(requestBody);
        JsonNode modelNode = body.get("model");
        if (modelNode == null || !modelNode.isTextual() || modelNode.asText().isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "企业模型请求缺少 model");
        }
        String publicModelId = modelNode.asText().trim();
        Set<ModelCapability> required = requiredCapabilities(endpoint, body, primaryCapability);
        ResolvedModel resolved = catalogService.resolve(publicModelId, primaryCapability);
        if (!resolved.model().probedCapabilities().containsAll(required)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "企业模型未通过请求所需能力探测");
        }
        body.put("model", resolved.upstreamModelId());
        try {
            return new PreparedModelGatewayRequest(
                    endpoint,
                    publicModelId,
                    required,
                    resolved,
                    objectMapper.writeValueAsBytes(body));
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "企业模型请求转换失败");
        }
    }

    /** 转发已准备请求，并在响应完成后写入无内容的每日聚合增量。 */
    @Override
    public Mono<Void> forward(
            ServerWebExchange exchange,
            PreparedModelGatewayRequest request,
            ModelGatewayCaller caller,
            String traceId) {
        Objects.requireNonNull(exchange);
        Objects.requireNonNull(request);
        Objects.requireNonNull(caller);
        return forwardBody(
                exchange,
                request.endpoint(),
                request.publicModelId(),
                request.resolvedModel(),
                caller,
                traceId,
                BodyInserters.fromValue(request.upstreamBody()),
                MediaType.APPLICATION_JSON);
    }

    /** 校验音频转写表单并流式重建 multipart，仅改写 model 字段。 */
    @Override
    public PreparedModelGatewayMultipartRequest prepareMultipart(
            ServerWebExchange exchange,
            MultiValueMap<String, Part> parts) {
        String endpoint = endpoint(exchange);
        if (!"/audio/transcriptions".equals(endpoint)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "企业模型网关端点不存在");
        }
        Part modelPart = parts == null ? null : parts.getFirst("model");
        if (!(modelPart instanceof FormFieldPart formModel) || formModel.value().isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "音频转写请求缺少 model");
        }
        if (!(parts.getFirst("file") instanceof FilePart)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "音频转写请求缺少 file");
        }
        String publicModelId = formModel.value().trim();
        ResolvedModel resolved = catalogService.resolve(publicModelId, ModelCapability.TRANSCRIPTION);
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        for (Map.Entry<String, List<Part>> entry : parts.entrySet()) {
            for (Part part : entry.getValue()) {
                if ("model".equals(entry.getKey())) {
                    continue;
                }
                copyPart(builder, entry.getKey(), part);
            }
        }
        builder.part("model", resolved.upstreamModelId());
        return new PreparedModelGatewayMultipartRequest(
                endpoint,
                publicModelId,
                resolved,
                builder.build());
    }

    /** 音频文件始终以响应式流转发，供应商密钥和委托不会进入 multipart 表单。 */
    @Override
    public Mono<Void> forwardMultipart(
            ServerWebExchange exchange,
            PreparedModelGatewayMultipartRequest request,
            ModelGatewayCaller caller,
            String traceId) {
        Objects.requireNonNull(request);
        return forwardBody(
                exchange,
                request.endpoint(),
                request.publicModelId(),
                request.resolvedModel(),
                Objects.requireNonNull(caller),
                traceId,
                BodyInserters.fromMultipartData(request.upstreamParts()),
                MediaType.MULTIPART_FORM_DATA);
    }

    private Mono<Void> forwardBody(
            ServerWebExchange exchange,
            String endpoint,
            String publicModelId,
            ResolvedModel resolvedModel,
            ModelGatewayCaller caller,
            String traceId,
            BodyInserter<?, ? super ClientHttpRequest> bodyInserter,
            MediaType contentType) {
        Objects.requireNonNull(exchange);
        String safeTraceId = traceId == null || traceId.isBlank() ? "trace_model_gateway" : traceId;
        Instant startedAt = clock.instant();
        UsageCapture capture = new UsageCapture(MAX_USAGE_CAPTURE_BYTES);
        AtomicBoolean success = new AtomicBoolean(false);
        String targetUrl = OpenAiUpstreamSupport.targetUrl(
                resolvedModel.provider().provider().baseUrl(), endpoint, null);
        Sinks.One<Void> responseHeadersReady = Sinks.one();

        Mono<Void> requestMono = webClient.post()
                .uri(URI.create(targetUrl))
                .headers(headers -> applyUpstreamHeaders(
                        headers, exchange, resolvedModel, caller, safeTraceId, contentType))
                .body(bodyInserter)
                .exchangeToMono(response -> {
                    responseHeadersReady.tryEmitEmpty();
                    success.set(response.statusCode().is2xxSuccessful());
                    if (!response.statusCode().is2xxSuccessful()) {
                        return writeSanitizedUpstreamError(exchange, response, capture);
                    }
                    return writeSuccessfulResponse(exchange, response, capture);
                });

        // 只限制响应头到达时间，不能给完整 SSE 生命周期设置总时长上限。
        Mono<Void> responseHeaderTimeout = responseHeadersReady.asMono()
                .timeout(FIRST_RESPONSE_TIMEOUT)
                .onErrorMap(ignored -> new PlatformException(
                        ErrorCode.OPENCODE_TIMEOUT, "企业模型供应商响应超时"))
                .then(Mono.never());
        Mono<Void> forwarding = Mono.firstWithSignal(requestMono, responseHeaderTimeout)
                .onErrorMap(this::sanitizeForwardingError);
        return forwarding
                .then(Mono.defer(() -> recordUsage(
                        endpoint, publicModelId, resolvedModel, caller, capture, success.get(), startedAt)))
                .onErrorResume(error -> Mono.defer(() -> recordUsage(
                                endpoint, publicModelId, resolvedModel, caller, capture, false, startedAt))
                        .then(Mono.error(error)));
    }

    private void copyPart(MultipartBodyBuilder builder, String name, Part part) {
        if (part instanceof FormFieldPart field) {
            builder.part(name, field.value());
            return;
        }
        var partBuilder = builder.asyncPart(name, limitedPartContent(part), DataBuffer.class);
        if (part instanceof FilePart filePart) {
            partBuilder.filename(filePart.filename());
        }
        MediaType contentType = part.headers().getContentType();
        if (contentType != null) {
            partBuilder.contentType(contentType);
        }
    }

    private Flux<DataBuffer> limitedPartContent(Part part) {
        AtomicLong seen = new AtomicLong();
        return part.content().handle((buffer, sink) -> {
            long total = seen.addAndGet(buffer.readableByteCount());
            if (total > MAX_MULTIPART_PART_BYTES) {
                DataBufferUtils.release(buffer);
                sink.error(new PlatformException(
                        ErrorCode.PAYLOAD_TOO_LARGE,
                        "音频转写单个表单部分超过 100 MiB 上限"));
                return;
            }
            sink.next(buffer);
        });
    }

    private Mono<Void> writeSuccessfulResponse(
            ServerWebExchange exchange,
            ClientResponse response,
            UsageCapture capture) {
        ServerHttpResponse target = exchange.getResponse();
        target.setStatusCode(response.statusCode());
        copySafeResponseHeaders(target.getHeaders(), response.headers().asHttpHeaders());
        Flux<DataBuffer> body = withStreamingTimeouts(response.bodyToFlux(DataBuffer.class))
                .doOnNext(capture::append);
        return target.writeWith(body);
    }

    private Mono<Void> writeSanitizedUpstreamError(
            ServerWebExchange exchange,
            ClientResponse response,
            UsageCapture capture) {
        // 必须消费并丢弃第三方错误正文，禁止将供应商 URL、请求内容或原始异常透传给 LobeHub。
        return response.bodyToFlux(DataBuffer.class)
                .doOnNext(capture::append)
                .then()
                .onErrorResume(ignored -> Mono.empty())
                .then(Mono.defer(() -> {
                    ServerHttpResponse target = exchange.getResponse();
                    target.setStatusCode(response.statusCode());
                    target.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    byte[] safeBody = ("{\"error\":{\"type\":\"upstream_error\",\"message\":\"企业模型供应商请求失败\"}}")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    return target.writeWith(Mono.just(target.bufferFactory().wrap(safeBody)));
                }));
    }

    private Mono<Void> recordUsage(
            String endpoint,
            String publicModelId,
            ResolvedModel resolvedModel,
            ModelGatewayCaller caller,
            UsageCapture capture,
            boolean succeeded,
            Instant startedAt) {
        TokenUsage tokenUsage = parseUsage(capture.bytes());
        long duration = Math.max(0L, Duration.between(startedAt, clock.instant()).toMillis());
        ModelGatewayUsageDelta delta = new ModelGatewayUsageDelta(
                clock.instant().atZone(ZoneOffset.UTC).toLocalDate(),
                caller.sourceClient(),
                caller.userId(),
                resolvedModel.provider().provider().providerId(),
                publicModelId,
                endpoint,
                succeeded,
                tokenUsage.inputTokens(),
                tokenUsage.outputTokens(),
                tokenUsage.totalTokens(),
                duration);
        // 聚合失败不能反向破坏模型响应；仓储实现自身必须保证原子累加。
        return Mono.fromRunnable(() -> usageRepository.increment(delta))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ignored -> Mono.empty())
                .then();
    }

    private TokenUsage parseUsage(byte[] responseBytes) {
        if (responseBytes.length == 0) {
            return TokenUsage.EMPTY;
        }
        String payload = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
        JsonNode usage = null;
        try {
            if (payload.stripLeading().startsWith("{")) {
                usage = objectMapper.readTree(payload).path("usage");
            } else {
                for (String line : payload.split("\\R")) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring("data:".length()).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        continue;
                    }
                    JsonNode candidate = objectMapper.readTree(data).path("usage");
                    if (candidate.isObject()) {
                        usage = candidate;
                    }
                }
            }
        } catch (IOException ignored) {
            return TokenUsage.EMPTY;
        }
        if (usage == null || !usage.isObject()) {
            return TokenUsage.EMPTY;
        }
        long input = nonNegative(usage.path("prompt_tokens").asLong(usage.path("input_tokens").asLong(0L)));
        long output = nonNegative(usage.path("completion_tokens").asLong(usage.path("output_tokens").asLong(0L)));
        long total = nonNegative(usage.path("total_tokens").asLong(input + output));
        return new TokenUsage(input, output, total);
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private Set<ModelCapability> requiredCapabilities(
            String endpoint,
            ObjectNode body,
            ModelCapability primaryCapability) {
        EnumSet<ModelCapability> required = EnumSet.of(primaryCapability);
        if ("/chat/completions".equals(endpoint) || "/responses".equals(endpoint)) {
            if ((body.path("tools").isArray() && !body.path("tools").isEmpty())
                    || body.hasNonNull("tool_choice")) {
                required.add(ModelCapability.TOOLS);
            }
            // 只检查承载用户内容的字段；工具参数或结构化输出 Schema 也可能声明 image_url，
            // 但声明本身不是图片输入，不能因此误要求模型通过 VISION 探测。
            JsonNode content = "/chat/completions".equals(endpoint)
                    ? body.path("messages")
                    : body.path("input");
            if (containsImageInput(content)) {
                required.add(ModelCapability.VISION);
            }
            if (body.hasNonNull("reasoning") || body.hasNonNull("reasoning_effort")) {
                required.add(ModelCapability.REASONING);
            }
        }
        return Set.copyOf(required);
    }

    private boolean containsImageInput(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            JsonNode type = node.get("type");
            if (type != null && type.isTextual()
                    && Set.of("image", "image_url", "input_image").contains(type.asText())) {
                return true;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (("image_url".equals(entry.getKey()) || "input_image".equals(entry.getKey()))
                        && !entry.getValue().isNull()) {
                    return true;
                }
                if (containsImageInput(entry.getValue())) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsImageInput(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private ObjectNode parseObject(byte[] requestBody) {
        try {
            JsonNode root = objectMapper.readTree(requestBody == null ? new byte[0] : requestBody);
            if (!(root instanceof ObjectNode objectNode)) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "企业模型请求体必须是 JSON 对象");
            }
            return objectNode;
        } catch (PlatformException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "企业模型请求体不是合法 JSON");
        }
    }

    private String endpoint(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (path == null || !path.startsWith(GATEWAY_PATH)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "企业模型网关端点不存在");
        }
        String endpoint = path.substring(GATEWAY_PATH.length());
        return endpoint.isBlank() ? "/" : endpoint;
    }

    private void applyUpstreamHeaders(
            HttpHeaders headers,
            ServerWebExchange exchange,
            ResolvedModel resolvedModel,
            ModelGatewayCaller caller,
            String traceId,
            MediaType contentType) {
        List<MediaType> accept = exchange.getRequest().getHeaders().getAccept();
        OpenAiUpstreamSupport.applyTrustedRequestHeaders(
                headers,
                resolvedModel.provider().authToken(),
                caller.unifiedAuthId(),
                traceId,
                contentType,
                accept);
        // 不复制 Authorization、provider、UCID 或其他客户端 Header。
    }

    private void copySafeResponseHeaders(HttpHeaders target, HttpHeaders source) {
        OpenAiUpstreamSupport.copySafeResponseHeaders(target, source, true);
    }

    private Flux<DataBuffer> withStreamingTimeouts(Flux<DataBuffer> body) {
        return body.timeout(Mono.delay(FIRST_EVENT_TIMEOUT), ignored -> Mono.delay(STREAM_IDLE_TIMEOUT));
    }

    private Throwable sanitizeForwardingError(Throwable error) {
        if (error instanceof PlatformException) {
            return error;
        }
        if (isTimeout(error)) {
            return new PlatformException(ErrorCode.OPENCODE_TIMEOUT, "企业模型供应商响应超时");
        }
        return new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "企业模型供应商连接失败");
    }

    private static boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.util.concurrent.TimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static WebClient defaultWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .responseTimeout(STREAM_IDLE_TIMEOUT);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private record TokenUsage(long inputTokens, long outputTokens, long totalTokens) {
        private static final TokenUsage EMPTY = new TokenUsage(0L, 0L, 0L);
    }

    private static final class UsageCapture {
        private final int maxBytes;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private UsageCapture(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        private synchronized void append(DataBuffer buffer) {
            int remaining = maxBytes - output.size();
            if (remaining <= 0) {
                return;
            }
            var bytes = buffer.asByteBuffer().asReadOnlyBuffer();
            int length = Math.min(remaining, bytes.remaining());
            byte[] chunk = new byte[length];
            bytes.get(chunk);
            output.writeBytes(chunk);
        }

        private synchronized void append(byte[] bytes) {
            int length = Math.min(maxBytes - output.size(), bytes.length);
            if (length > 0) {
                output.write(bytes, 0, length);
            }
        }

        private synchronized byte[] bytes() {
            return output.toByteArray();
        }
    }
}
