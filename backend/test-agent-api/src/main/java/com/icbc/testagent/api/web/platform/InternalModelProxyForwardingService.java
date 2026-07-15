package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.configuration.InternalModelProvider;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.opencode.runtime.internalmodel.InternalModelProviderRegistry;
import com.icbc.testagent.opencode.runtime.internalmodel.InternalModelProxyRuntimeSettings;
import com.icbc.testagent.opencode.runtime.internalmodel.InternalModelThinkStreamConverter;
import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

    public static final String PROVIDER_HEADER = "X-ICBC-Model-Provider";
    public static final String UCID_HEADER = "ucid";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration FIRST_RESPONSE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration FIRST_EVENT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofSeconds(120);

    private final InternalModelProviderRegistry registry;
    private final InternalModelProxyRuntimeSettings settings;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
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
            ObjectMapper objectMapper) {
        this(registry, settings, defaultWebClient(), objectMapper);
    }

    InternalModelProxyForwardingService(
            InternalModelProviderRegistry registry,
            InternalModelProxyRuntimeSettings settings,
            WebClient webClient,
            ObjectMapper objectMapper) {
        this(
                registry,
                settings,
                webClient,
                objectMapper,
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
            Duration firstResponseTimeout,
            Duration firstEventTimeout,
            Duration streamIdleTimeout) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.webClient = Objects.requireNonNull(webClient, "webClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.firstResponseTimeout = requirePositive(firstResponseTimeout, "firstResponseTimeout");
        this.firstEventTimeout = requirePositive(firstEventTimeout, "firstEventTimeout");
        this.streamIdleTimeout = requirePositive(streamIdleTimeout, "streamIdleTimeout");
    }

    public Mono<Void> forward(ServerWebExchange exchange, String body, String traceId) {
        validateProxyAuth(exchange);
        String providerId = exchange.getRequest().getHeaders().getFirst(PROVIDER_HEADER);
        InternalModelProvider provider = registry.requireProvider(providerId);
        validateModel(body);
        String targetUrl = targetUrl(provider.baseUrl(), downstreamPath(exchange), exchange.getRequest().getURI().getRawQuery());
        InternalModelThinkStreamConverter converter = new InternalModelThinkStreamConverter(objectMapper);
        Sinks.One<Void> responseHeadersReady = Sinks.one();
        Mono<Void> request = webClient.method(exchange.getRequest().getMethod() == null ? HttpMethod.POST : exchange.getRequest().getMethod())
                .uri(URI.create(targetUrl))
                .headers(headers -> applyForwardHeaders(headers, exchange, traceId))
                .body(BodyInserters.fromValue(body == null ? "" : body))
                .exchangeToMono(response -> {
                    responseHeadersReady.tryEmitEmpty();
                    return writeResponse(exchange, response, converter);
                });
        Mono<Void> responseHeaderTimeout = responseHeadersReady.asMono()
                .timeout(firstResponseTimeout)
                .then(Mono.never());
        return Mono.firstWithSignal(request, responseHeaderTimeout);
    }

    private Mono<Void> writeResponse(
            ServerWebExchange exchange,
            ClientResponse response,
            InternalModelThinkStreamConverter converter) {
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

        Flux<ServerSentEvent<String>> events = withStreamingTimeouts(response.bodyToFlux(SSE_EVENT_TYPE))
                .map(event -> convertEvent(event, converter));
        return sseWriter.write(
                events,
                SSE_EVENT_RESOLVABLE_TYPE,
                contentType,
                targetResponse,
                Collections.emptyMap());
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

    private void validateProxyAuth(ServerWebExchange exchange) {
        String token = com.icbc.testagent.api.web.common.AuthWebSupport.extractBearerToken(exchange);
        if (!settings.matchesApiKey(token)) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "内部模型代理鉴权失败");
        }
    }

    private void validateModel(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
            JsonNode model = root.get("model");
            if (model == null || !model.isTextual() || model.asText().isBlank()) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型代理请求缺少 model");
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型代理请求体不是合法 JSON");
        }
    }

    private void applyForwardHeaders(HttpHeaders headers, ServerWebExchange exchange, String traceId) {
        headers.setBearerAuth(registry.requireAuthToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(exchange.getRequest().getHeaders().getAccept());
        headers.set(TraceConstants.TRACE_ID_HEADER, traceId);
        String ucid = exchange.getRequest().getHeaders().getFirst(UCID_HEADER);
        if (ucid != null && !ucid.isBlank()) {
            headers.set(UCID_HEADER, ucid);
        }
    }

    private void copyResponseHeaders(HttpHeaders target, HttpHeaders source, boolean transformSse) {
        MediaType contentType = source.getContentType();
        if (contentType != null) {
            target.setContentType(contentType);
        }
        // 非 SSE 分支按字节转发正文，必须同步保留内容编码；SSE 解码重编码后不能沿用上游编码。
        if (!transformSse) {
            copyHeader(target, source, HttpHeaders.CONTENT_ENCODING);
        }
        copyHeader(target, source, HttpHeaders.RETRY_AFTER);
        copyHeader(target, source, TraceConstants.TRACE_ID_HEADER);
        copyHeader(target, source, HttpHeaders.CACHE_CONTROL);
    }

    private void copyHeader(HttpHeaders target, HttpHeaders source, String headerName) {
        List<String> values = source.get(headerName);
        if (values != null && !values.isEmpty()) {
            target.put(headerName, List.copyOf(values));
        }
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

    private String targetUrl(String baseUrl, String path, String rawQuery) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return rawQuery == null || rawQuery.isBlank()
                ? normalizedBase + normalizedPath
                : normalizedBase + normalizedPath + "?" + rawQuery;
    }
}
