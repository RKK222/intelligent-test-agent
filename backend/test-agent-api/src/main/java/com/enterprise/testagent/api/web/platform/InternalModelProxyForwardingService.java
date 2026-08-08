package com.enterprise.testagent.api.web.platform;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecord;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallSource;
import com.enterprise.testagent.model.gateway.OpenAiUpstreamSupport;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProviderRegistry;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProxyRuntimeSettings;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelResponsesAdapter;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelSseStreamObserver;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelSseStreamObserver.ObservedEvent;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelThinkStreamConverter;
import com.enterprise.testagent.opencode.runtime.internalmodel.observability.InternalModelCallOutcomeClassifier;
import com.enterprise.testagent.opencode.runtime.internalmodel.observability.InternalModelCallRecorder;
import io.netty.channel.ChannelOption;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.http.client.HttpClient;

/**
 * 内部模型代理转发服务，封装鉴权、供应商选择、上游鉴权注入和 SSE 思考内容转换。
 */
@Service
public class InternalModelProxyForwardingService {

    public static final String PROVIDER_HEADER = OpenAiUpstreamSupport.PROVIDER_HEADER;
    public static final String UCID_HEADER = OpenAiUpstreamSupport.UCID_HEADER;

    private static final String RESPONSES_PATH = "/responses";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration FIRST_RESPONSE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration FIRST_EVENT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofSeconds(120);
    private static final String UNKNOWN_DIMENSION = "unknown";

    private final InternalModelProviderRegistry registry;
    private final InternalModelProxyRuntimeSettings settings;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final InternalModelSseStreamObserver sseStreamObserver;
    private final InternalModelCallRecorder recorder;
    private final Duration firstResponseTimeout;
    private final Duration firstEventTimeout;
    private final Duration streamIdleTimeout;
    private final ServerSentEventHttpMessageWriter sseWriter = new ServerSentEventHttpMessageWriter();

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_EVENT_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ResolvableType SSE_EVENT_RESOLVABLE_TYPE =
            ResolvableType.forClassWithGenerics(ServerSentEvent.class, String.class);

    @Autowired
    public InternalModelProxyForwardingService(
            InternalModelProviderRegistry registry,
            InternalModelProxyRuntimeSettings settings,
            ObjectMapper objectMapper,
            InternalModelCallRecorder recorder) {
        this(registry, settings, defaultWebClient(), objectMapper, recorder);
    }

    InternalModelProxyForwardingService(
            InternalModelProviderRegistry registry,
            InternalModelProxyRuntimeSettings settings,
            WebClient webClient,
            ObjectMapper objectMapper,
            InternalModelCallRecorder recorder) {
        this(
                registry,
                settings,
                webClient,
                objectMapper,
                recorder,
                FIRST_RESPONSE_TIMEOUT,
                FIRST_EVENT_TIMEOUT,
                STREAM_IDLE_TIMEOUT);
    }

    /**
     * 测试构造器允许缩短响应与事件等待时间，生产构造始终使用固定的 30/30/120 秒边界。
     */
    InternalModelProxyForwardingService(
            InternalModelProviderRegistry registry,
            InternalModelProxyRuntimeSettings settings,
            WebClient webClient,
            ObjectMapper objectMapper,
            InternalModelCallRecorder recorder,
            Duration firstResponseTimeout,
            Duration firstEventTimeout,
            Duration streamIdleTimeout) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.webClient = Objects.requireNonNull(webClient, "webClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.sseStreamObserver = new InternalModelSseStreamObserver(objectMapper);
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
        this.firstResponseTimeout = requirePositive(firstResponseTimeout, "firstResponseTimeout");
        this.firstEventTimeout = requirePositive(firstEventTimeout, "firstEventTimeout");
        this.streamIdleTimeout = requirePositive(streamIdleTimeout, "streamIdleTimeout");
    }

    public Mono<Void> forward(ServerWebExchange exchange, byte[] body, String traceId) {
        return forward(exchange, body, traceId, prepareRequest(exchange));
    }

    /**
     * 在订阅请求体之前完成代理密钥校验和供应商快照解析，拒绝无效请求的聚合内存占用。
     */
    PreparedRequest prepareRequest(ServerWebExchange exchange) {
        validateProxyAuth(exchange);
        String providerId = exchange.getRequest().getHeaders().getFirst(PROVIDER_HEADER);
        return new PreparedRequest(registry.requireRuntimeConfig(providerId));
    }

    Mono<Void> forward(
            ServerWebExchange exchange,
            byte[] body,
            String traceId,
            PreparedRequest preparedRequest) {
        InternalModelProviderRuntimeConfig runtimeConfig =
                Objects.requireNonNull(preparedRequest, "preparedRequest must not be null").runtimeConfig();
        InternalModelProvider provider = runtimeConfig.provider();
        String requestedPath = downstreamPath(exchange);
        boolean responsesRequest = RESPONSES_PATH.equals(requestedPath);
        InternalModelResponsesAdapter responsesAdapter = new InternalModelResponsesAdapter(objectMapper);
        byte[] upstreamBody;
        String upstreamPath;
        InternalModelResponsesAdapter.StreamSession responsesSession;
        String model;
        if (responsesRequest) {
            InternalModelResponsesAdapter.ConvertedRequest converted = responsesAdapter.convertRequest(body);
            upstreamBody = converted.body();
            upstreamPath = CHAT_COMPLETIONS_PATH;
            model = converted.model();
            responsesSession = responsesAdapter.newStreamSession(model);
        } else {
            model = validateAndExtractModel(body);
            upstreamBody = body == null ? new byte[0] : body;
            upstreamPath = requestedPath;
            responsesSession = null;
        }
        String targetUrl = OpenAiUpstreamSupport.targetUrl(
                provider.baseUrl(), upstreamPath, exchange.getRequest().getURI().getRawQuery());
        InternalModelThinkStreamConverter converter = new InternalModelThinkStreamConverter(objectMapper);
        CallObservation observation = new CallObservation(
                provider.providerId(), model, requestedPath, traceId,
                exchange.getRequest().getHeaders().getFirst(UCID_HEADER));
        Sinks.One<Void> responseHeadersReady = Sinks.one();
        Mono<Void> request = webClient.method(exchange.getRequest().getMethod() == null ? HttpMethod.POST : exchange.getRequest().getMethod())
                .uri(URI.create(targetUrl))
                .headers(headers -> applyForwardHeaders(
                        headers, exchange, traceId, runtimeConfig.authToken(), responsesRequest))
                .body(BodyInserters.fromValue(upstreamBody))
                .exchangeToMono(response -> {
                    responseHeadersReady.tryEmitEmpty();
                    observation.markFirstByte(response.statusCode());
                    return writeResponse(exchange, response, converter, responsesSession, observation);
                });
        Mono<Void> responseHeaderTimeout = responseHeadersReady.asMono()
                .timeout(firstResponseTimeout)
                .then(Mono.never());
        return Mono.firstWithSignal(request, responseHeaderTimeout)
                .doOnError(error -> observation.markError(error))
                .doOnCancel(observation::markCancelled)
                .doFinally(signal -> recordTerminal(observation));
    }

    private Mono<Void> writeResponse(
            ServerWebExchange exchange,
            ClientResponse response,
            InternalModelThinkStreamConverter converter,
            InternalModelResponsesAdapter.StreamSession responsesSession,
            CallObservation observation) {
        ServerHttpResponse targetResponse = exchange.getResponse();
        targetResponse.setStatusCode(response.statusCode());

        MediaType contentType = response.headers().contentType().orElse(null);
        boolean transformSse = response.statusCode().is2xxSuccessful()
                && contentType != null
                && MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType);
        copyResponseHeaders(targetResponse.getHeaders(), response.headers().asHttpHeaders(), transformSse);
        if (!transformSse) {
            return targetResponse.writeWith(withStreamingTimeouts(response.bodyToFlux(DataBuffer.class)));
        }

        observation.markStreaming();
        Flux<ServerSentEvent<String>> upstreamEvents = withSseTimeouts(response.bodyToFlux(SSE_EVENT_TYPE))
                .doOnNext(observation::markSseEvent)
                // [DONE] 是 OpenAI 兼容流的协议终点；此处主动取消仍保持连接的异常上游。
                .takeUntil(ObservedEvent::done)
                .map(ObservedEvent::event);
        Flux<ServerSentEvent<String>> events;
        if (responsesSession == null) {
            events = upstreamEvents
                    .map(event -> convertEvent(event, converter))
                    .concatWith(Flux.defer(() -> {
                        observation.markDirectStreamEnd();
                        return Flux.empty();
                    }));
        } else {
            events = upstreamEvents
                    .concatMapIterable(event -> convertResponsesEvent(event, converter, responsesSession))
                    .concatWith(Flux.defer(() -> Flux.fromIterable(markInterrupted(
                            convertResponsesFailure(
                                    responsesSession,
                                    "upstream_stream_interrupted",
                                    "上游模型流在完成前结束"),
                            observation))))
                    .onErrorResume(error -> Flux.fromIterable(markStreamFailure(
                            convertResponsesFailure(
                                    responsesSession,
                                    "upstream_stream_failed",
                                    "上游模型流读取失败"),
                            observation, error)));
        }
        return sseWriter.write(
                events,
                SSE_EVENT_RESOLVABLE_TYPE,
                contentType,
                targetResponse,
                Collections.emptyMap());
    }

    /**
     * 记录 prepareRequest 阶段的同步失败：代理鉴权失败 -> PROXY_AUTH_FAILED；
     * provider 未启用/不存在/Token 未配置 -> PROVIDER_UNAVAILABLE。调用方在捕获后原样抛出。
     */
    void recordPrepareRequestFailure(ServerWebExchange exchange, String traceId, PlatformException error) {
        InternalModelCallOutcome outcome = error.errorCode() == ErrorCode.UNAUTHENTICATED
                ? InternalModelCallOutcome.PROXY_AUTH_FAILED
                : InternalModelCallOutcome.PROVIDER_UNAVAILABLE;
        recordStageFailure(exchange, traceId, outcome, error);
    }

    /**
     * 记录请求体校验阶段的同步失败（缺 model、非法 JSON、2 MiB 上限、responses 转换失败）：
     * 一律归为 REQUEST_INVALID。调用方在捕获后原样抛出。
     */
    void recordRequestValidationFailure(ServerWebExchange exchange, String traceId, PlatformException error) {
        recordStageFailure(exchange, traceId, InternalModelCallOutcome.REQUEST_INVALID, error);
    }

    private void recordStageFailure(
            ServerWebExchange exchange,
            String traceId,
            InternalModelCallOutcome outcome,
            PlatformException error) {
        recordTerminal(CallObservation.stageFailure(
                exchange, traceId, outcome, InternalModelCallOutcomeClassifier.errorClass(error)));
    }

    private void recordTerminal(CallObservation observation) {
        try {
            if (observation.terminalRecorded.compareAndSet(false, true)) {
                recorder.record(observation.toRecord()).subscribe();
            }
        } catch (RuntimeException ignored) {
            // 观测记录失败绝不影响转发主链路。
        }
    }

    private static List<ServerSentEvent<String>> markInterrupted(
            List<ServerSentEvent<String>> events, CallObservation observation) {
        // 只有补偿分支实际产生事件（流未在完成前正常结束）才标记中断，避免误报。
        if (!events.isEmpty()) {
            observation.markStreamOutcome(InternalModelCallOutcome.UPSTREAM_STREAM_INTERRUPTED);
        }
        return events;
    }

    private static List<ServerSentEvent<String>> markStreamFailure(
            List<ServerSentEvent<String>> events, CallObservation observation, Throwable error) {
        // 上游流错误若属超时（首事件/流空闲），保留精确分类；否则归为流读取失败。
        InternalModelCallOutcome classified =
                InternalModelCallOutcomeClassifier.classify(error, observation.signals());
        if (classified == InternalModelCallOutcome.UNKNOWN_ERROR) {
            observation.markStreamOutcome(InternalModelCallOutcome.UPSTREAM_STREAM_FAILED);
        } else {
            observation.markError(error);
        }
        return events;
    }

    private static WebClient defaultWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                // 连接建立后的网络空闲边界与 SSE 事件空闲边界一致；首个事件由响应 Flux 单独限制为 30 秒。
                .responseTimeout(STREAM_IDLE_TIMEOUT);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 连接建立后不限制整个 SSE 生命周期，只限制首个事件和相邻事件之间的空闲时间。
     * timeout 的取消信号会沿响应 Flux 传回 WebClient，从而关闭上游连接。
     */
    <T> Flux<T> withStreamingTimeouts(Flux<T> source) {
        return source.timeout(
                Mono.delay(firstEventTimeout),
                ignored -> Mono.delay(streamIdleTimeout));
    }

    /** SSE 注释、元数据和伪心跳都不能刷新有效输出截止时间。 */
    Flux<ObservedEvent> withSseTimeouts(Flux<ServerSentEvent<String>> source) {
        return sseStreamObserver.observe(source, firstEventTimeout, streamIdleTimeout);
    }

    private static Duration requirePositive(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }

    private ServerSentEvent<String> convertEvent(
            ServerSentEvent<String> event,
            InternalModelThinkStreamConverter converter) {
        ServerSentEvent.Builder<String> builder = ServerSentEvent.builder();
        if (event.id() != null) {
            builder.id(event.id());
        }
        if (event.event() != null) {
            builder.event(event.event());
        }
        if (event.retry() != null) {
            builder.retry(event.retry());
        }
        if (event.comment() != null) {
            builder.comment(event.comment());
        }
        if (event.data() != null) {
            builder.data(converter.convertData(event.data()));
        }
        return builder.build();
    }

    private List<ServerSentEvent<String>> convertResponsesEvent(
            ServerSentEvent<String> event,
            InternalModelThinkStreamConverter converter,
            InternalModelResponsesAdapter.StreamSession session) {
        if (event.data() == null) {
            return List.of();
        }
        return session.convertData(converter.convertData(event.data())).stream()
                .map(converted -> ServerSentEvent.<String>builder()
                        .event(converted.event())
                        .data(converted.data())
                        .build())
                .toList();
    }

    private List<ServerSentEvent<String>> convertResponsesFailure(
            InternalModelResponsesAdapter.StreamSession session,
            String code,
            String message) {
        return session.failIfIncomplete(code, message).stream()
                .map(converted -> ServerSentEvent.<String>builder()
                        .event(converted.event())
                        .data(converted.data())
                        .build())
                .toList();
    }

    private void validateProxyAuth(ServerWebExchange exchange) {
        String token = com.enterprise.testagent.api.web.common.AuthWebSupport.extractBearerToken(exchange);
        if (!settings.matchesApiKey(token)) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "内部模型代理鉴权失败");
        }
    }

    private String validateAndExtractModel(byte[] body) {
        byte[] requestBody = body == null ? new byte[0] : body;
        try (JsonParser parser = objectMapper.getFactory().createParser(requestBody)) {
            JsonToken rootToken = parser.nextToken();
            if (rootToken != JsonToken.START_OBJECT) {
                if (rootToken != null) {
                    parser.skipChildren();
                    requireDocumentEnd(parser);
                }
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型代理请求缺少 model");
            }

            String model = null;
            boolean textualModel = false;
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (token == null || token != JsonToken.FIELD_NAME) {
                    throw new IOException("内部模型代理请求体对象未正常结束");
                }
                String fieldName = parser.currentName();
                JsonToken valueToken = parser.nextToken();
                if (valueToken == null) {
                    throw new IOException("内部模型代理请求字段缺少值");
                }
                if ("model".equals(fieldName)) {
                    textualModel = valueToken == JsonToken.VALUE_STRING;
                    model = textualModel ? parser.getValueAsString() : null;
                }
                parser.skipChildren();
            }
            requireDocumentEnd(parser);
            if (!textualModel || model == null || model.isBlank()) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型代理请求缺少 model");
            }
            return model;
        } catch (PlatformException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型代理请求体不是合法 JSON");
        }
    }

    /** 流式扫描仍消费完整文档，避免找到 model 后忽略尾部畸形 JSON 或额外根值。 */
    private void requireDocumentEnd(JsonParser parser) throws IOException {
        if (parser.nextToken() != null) {
            throw new IOException("内部模型代理请求体包含额外根值");
        }
    }

    private void applyForwardHeaders(
            HttpHeaders headers,
            ServerWebExchange exchange,
            String traceId,
            String authToken,
            boolean responsesRequest) {
        String ucid = exchange.getRequest().getHeaders().getFirst(UCID_HEADER);
        List<MediaType> accept = responsesRequest
                ? List.of(MediaType.TEXT_EVENT_STREAM)
                : exchange.getRequest().getHeaders().getAccept();
        OpenAiUpstreamSupport.applyTrustedRequestHeaders(
                headers, authToken, ucid, traceId, MediaType.APPLICATION_JSON, accept);
    }

    private void copyResponseHeaders(HttpHeaders target, HttpHeaders source, boolean transformSse) {
        // 非 SSE 分支按字节转发正文，必须同步保留内容编码；SSE 解码重编码后不能沿用上游编码。
        OpenAiUpstreamSupport.copySafeResponseHeaders(target, source, !transformSse);
    }

    private String downstreamPath(ServerWebExchange exchange) {
        String fullPath = exchange.getRequest().getURI().getRawPath();
        String prefix = InternalModelProxyRuntimeSettings.PROXY_PATH;
        if (fullPath == null || !fullPath.startsWith(prefix)) {
            return "/";
        }
        String path = fullPath.substring(prefix.length());
        return path.isBlank() ? "/" : path;
    }

    /** 鉴权通过后固化同一代供应商与 Token 快照，后续读取请求体期间不会发生串代。 */
    record PreparedRequest(InternalModelProviderRuntimeConfig runtimeConfig) {

        PreparedRequest {
            Objects.requireNonNull(runtimeConfig, "runtimeConfig must not be null");
        }
    }

    /**
     * 单次转发的观测上下文。线程模型：Netty event loop 上并发访问，标记位与时间戳用原子类型保护，
     * 终态只用一次（terminalRecorded 防 doOnError 与 doFinally 双写）。不携带任何请求/响应正文。
     */
    static final class CallObservation {
        private final String providerId;
        private final String model;
        private final String endpoint;
        private final String traceId;
        private final String ucid;
        private final Instant startedAt;
        private final long startedNanos;
        private final AtomicBoolean firstByteMarked = new AtomicBoolean(false);
        private final AtomicBoolean firstEventMarked = new AtomicBoolean(false);
        private final AtomicBoolean doneSeen = new AtomicBoolean(false);
        private final AtomicBoolean streaming = new AtomicBoolean(false);
        private final AtomicBoolean streamOutcomeSet = new AtomicBoolean(false);
        private final AtomicBoolean outcomeExplicitlySet = new AtomicBoolean(false);
        private final AtomicBoolean terminalRecorded = new AtomicBoolean(false);
        private final AtomicLong httpStatus = new AtomicLong(-1);
        private volatile InternalModelCallOutcome outcome = InternalModelCallOutcome.SUCCESS;
        private volatile String errorClass;
        private volatile long firstByteNanos;
        private static final long UNSET_NANOS = Long.MIN_VALUE;
        private final AtomicLong firstTokenNanos = new AtomicLong(UNSET_NANOS);
        private final AtomicLong streamCompleteNanos = new AtomicLong(UNSET_NANOS);

        CallObservation(
                String providerId,
                String model,
                String endpoint,
                String traceId,
                String ucid) {
            this.providerId = providerId;
            this.model = model;
            this.endpoint = endpoint;
            this.traceId = traceId;
            this.ucid = ucid;
            this.startedAt = Instant.now();
            this.startedNanos = System.nanoTime();
        }

        /** 请求前同步阶段失败的观测：无转发时序，仅记录分类与耗时。 */
        static CallObservation stageFailure(
                ServerWebExchange exchange,
                String traceId,
                InternalModelCallOutcome outcome,
                String errorClass) {
            CallObservation observation = new CallObservation(
                    providerIdFromHeader(exchange), UNKNOWN_DIMENSION, pathFrom(exchange), traceId,
                    exchange.getRequest().getHeaders().getFirst(UCID_HEADER));
            observation.outcome = outcome;
            observation.errorClass = errorClass;
            observation.outcomeExplicitlySet.set(true);
            return observation;
        }

        void markFirstByte(HttpStatusCode statusCode) {
            firstByteNanos = System.nanoTime();
            httpStatus.set(statusCode.value());
            // 先写时间与状态，再发布标记，避免终态线程观察到 true 但读到默认值。
            firstByteMarked.set(true);
        }

        void markStreaming() {
            streaming.set(true);
        }

        void markSseEvent(ObservedEvent event) {
            if (event.done()) {
                doneSeen.set(true);
                if (hasFirstToken()) {
                    streamCompleteNanos.compareAndSet(UNSET_NANOS, event.receivedNanos());
                }
                return;
            }
            if (!event.output()) {
                return;
            }
            firstEventMarked.set(true);
            // 使用数据到达时刻而非 JSON 解析完成时刻，避免解析开销污染首 token 指标。
            firstTokenNanos.compareAndSet(UNSET_NANOS, event.receivedNanos());
        }

        void markDirectStreamEnd() {
            // Chat Completions 流必须有有效 chunk 并以 [DONE] 结束；正常 EOF 否则属于截断/空流。
            if (!hasFirstToken() || !doneSeen.get()) {
                markStreamOutcome(InternalModelCallOutcome.UPSTREAM_STREAM_INTERRUPTED);
            }
        }

        void markStreamOutcome(InternalModelCallOutcome streamOutcome) {
            // 流补偿分支的终态优先于超时/状态码判定，且只允许设置一次。
            if (streamOutcomeSet.compareAndSet(false, true)) {
                outcome = streamOutcome;
                outcomeExplicitlySet.set(true);
            }
        }

        void markError(Throwable error) {
            if (outcomeExplicitlySet.compareAndSet(false, true)) {
                outcome = InternalModelCallOutcomeClassifier.classify(error, signals());
                errorClass = InternalModelCallOutcomeClassifier.errorClass(error);
            }
        }

        void markCancelled() {
            // 下游 opencode 提前断开：仅当尚未被更精确的分类（错误/流补偿）覆盖时才标记，
            // 避免把真实的连接失败/超时错误误记为客户端断开。
            if (outcomeExplicitlySet.compareAndSet(false, true)) {
                outcome = InternalModelCallOutcome.CLIENT_DISCONNECTED;
            }
        }

        InternalModelCallOutcomeClassifier.TimeoutSignals signals() {
            return new InternalModelCallOutcomeClassifier.TimeoutSignals(
                    firstByteMarked.get(), firstEventMarked.get(), streaming.get());
        }

        InternalModelCallRecord toRecord() {
            long httpStatusValue = httpStatus.get();
            resolveOutcome(httpStatusValue);
            long durationMillis = (System.nanoTime() - startedNanos) / 1_000_000;
            Long firstByteMillis = firstByteMarked.get()
                    ? (firstByteNanos - startedNanos) / 1_000_000
                    : null;
            Long firstTokenMillis = hasFirstToken()
                    ? (firstTokenNanos.get() - startedNanos) / 1_000_000
                    : null;
            Long streamCompleteMillis = hasStreamComplete()
                    ? (streamCompleteNanos.get() - startedNanos) / 1_000_000
                    : null;
            return new InternalModelCallRecord(
                    null,
                    providerId,
                    model,
                    endpoint == null ? "/" : endpoint,
                    InternalModelCallSource.USER_CALL,
                    outcome,
                    httpStatusValue >= 0 ? (int) httpStatusValue : null,
                    errorClass,
                    streaming.get(),
                    durationMillis,
                    firstByteMillis,
                    firstTokenMillis,
                    streamCompleteMillis,
                    traceId == null ? "" : traceId,
                    ucid,
                    startedAt);
        }

        private boolean hasFirstToken() {
            return firstTokenNanos.get() != UNSET_NANOS;
        }

        private boolean hasStreamComplete() {
            return streamCompleteNanos.get() != UNSET_NANOS;
        }

        private void resolveOutcome(long httpStatusValue) {
            // 已由错误/取消/流补偿分支显式分类：保留该分类，不再按状态码或首字节推断覆盖。
            if (outcomeExplicitlySet.get()) {
                return;
            }
            if (firstByteMarked.get()) {
                outcome = httpStatusValue >= 200 && httpStatusValue < 300
                        ? InternalModelCallOutcome.SUCCESS
                        : InternalModelCallOutcome.UPSTREAM_HTTP_ERROR;
            } else if (outcome == InternalModelCallOutcome.SUCCESS) {
                // 上游响应头未到且无错误信号：视为未知（正常不会发生）。
                outcome = InternalModelCallOutcome.UNKNOWN_ERROR;
            }
        }

        private static String providerIdFromHeader(ServerWebExchange exchange) {
            String value = exchange.getRequest().getHeaders().getFirst(PROVIDER_HEADER);
            return value == null || value.isBlank() ? UNKNOWN_DIMENSION : value;
        }

        private static String pathFrom(ServerWebExchange exchange) {
            String fullPath = exchange.getRequest().getURI().getRawPath();
            if (fullPath == null || !fullPath.startsWith(InternalModelProxyRuntimeSettings.PROXY_PATH)) {
                return "/";
            }
            String path = fullPath.substring(InternalModelProxyRuntimeSettings.PROXY_PATH.length());
            return path.isBlank() ? "/" : path;
        }
    }
}
