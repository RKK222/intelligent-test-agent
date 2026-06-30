package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.api.ApiErrorResponse;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.observability.TraceConstants;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 后端 Java 之间的统一 HTTP 转发器，负责 header/body/trace 透传和统一错误转换。
 */
@Service
class BackendHttpForwarder {

    static final String ROUTED_HEADER = "X-Test-Agent-Backend-Routed";

    private static final Duration FORWARD_TIMEOUT = Duration.ofSeconds(30);
    private static final List<String> FORWARDED_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.ACCEPT,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT_LANGUAGE,
            TraceConstants.TRACE_ID_HEADER);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    BackendHttpForwarder(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    BackendHttpForwarder(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    /**
     * 透传原始 HTTP 请求，并把目标 Java 响应原样写回当前响应。
     */
    Mono<Void> forwardRaw(ServerWebExchange exchange, BackendJavaProcess backend) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(backend, "backend must not be null");
        return forwardRawResponse(exchange, backend)
                .flatMap(response -> writeRawResponse(exchange, response));
    }

    /**
     * 透传原始 HTTP 请求并返回目标响应，供调用方按状态码做兼容降级。
     */
    Mono<HttpResponse<byte[]>> forwardRawResponse(ServerWebExchange exchange, BackendJavaProcess backend) {
        return requestBody(exchange)
                .flatMap(body -> Mono.fromCallable(() -> sendRaw(exchange, backend, pathAndQuery(exchange), body))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 转发统一 JSON API 请求，并把目标响应解析回调用方。
     */
    <T> ApiResponse<T> forwardTyped(
            ServerWebExchange exchange,
            BackendJavaProcess backend,
            Object requestBody,
            TypeReference<ApiResponse<T>> responseType) {
        return forwardTyped(
                exchange,
                backend,
                pathAndQuery(exchange),
                exchange.getRequest().getMethod().name(),
                requestBody,
                responseType);
    }

    /**
     * 转发统一 JSON API 请求，允许调用方指定目标 path/method。
     */
    <T> ApiResponse<T> forwardTyped(
            ServerWebExchange exchange,
            BackendJavaProcess backend,
            String pathAndQuery,
            String method,
            Object requestBody,
            TypeReference<ApiResponse<T>> responseType) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(backend, "backend must not be null");
        Objects.requireNonNull(pathAndQuery, "pathAndQuery must not be null");
        Objects.requireNonNull(method, "method must not be null");
        try {
            HttpRequest.BodyPublisher body = requestBody == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody));
            HttpRequest.Builder builder = baseBuilder(exchange, backend, pathAndQuery)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json");
            HttpResponse<String> response = httpClient.send(
                    builder.method(method, body).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), responseType);
            }
            ApiErrorResponse error = objectMapper.readValue(response.body(), ApiErrorResponse.class);
            throw new PlatformException(errorCode(error.code()), error.message(), error.details());
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(backend, exception);
        }
    }

    private HttpResponse<byte[]> sendRaw(
            ServerWebExchange exchange,
            BackendJavaProcess backend,
            String pathAndQuery,
            byte[] body) throws Exception {
        HttpRequest.BodyPublisher publisher = body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        return httpClient.send(
                baseBuilder(exchange, backend, pathAndQuery)
                        .method(exchange.getRequest().getMethod().name(), publisher)
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest.Builder baseBuilder(
            ServerWebExchange exchange,
            BackendJavaProcess backend,
            String pathAndQuery) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(backend.listenUrl()) + pathAndQuery))
                .timeout(FORWARD_TIMEOUT);
        for (String headerName : FORWARDED_HEADERS) {
            copyHeader(exchange, builder, headerName);
        }
        builder.header(ROUTED_HEADER, "true");
        if (exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER) == null) {
            builder.header(TraceConstants.TRACE_ID_HEADER, traceId(exchange, "trace_backend_forward"));
        }
        return builder;
    }

    private Mono<byte[]> requestBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(buffer -> {
                    byte[] result = bytes(buffer);
                    DataBufferUtils.release(buffer);
                    return result;
                })
                .defaultIfEmpty(new byte[0]);
    }

    Mono<Void> writeRawResponse(ServerWebExchange exchange, HttpResponse<byte[]> response) {
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(response.statusCode()));
        response.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                .ifPresent(value -> exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, value));
        response.headers().firstValue(TraceConstants.TRACE_ID_HEADER)
                .ifPresent(value -> exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, value));
        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        return exchange.getResponse().writeWith(Mono.just(bufferFactory.wrap(response.body())));
    }

    private void copyHeader(ServerWebExchange exchange, HttpRequest.Builder builder, String headerName) {
        List<String> values = exchange.getRequest().getHeaders().get(headerName);
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                builder.header(headerName, value);
            }
        }
    }

    private ErrorCode errorCode(String code) {
        try {
            return ErrorCode.valueOf(code);
        } catch (Exception exception) {
            return ErrorCode.INTERNAL_ERROR;
        }
    }

    private PlatformException unavailable(BackendJavaProcess backend, Exception cause) {
        return new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "目标服务器后端不可用",
                Map.of("linuxServerId", backend.linuxServerId().value()),
                cause);
    }

    private byte[] bytes(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return bytes;
    }

    private String traceId(ServerWebExchange exchange, String fallback) {
        String traceId = exchange.getResponse().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        }
        return traceId == null || traceId.isBlank() ? fallback : traceId;
    }

    private String pathAndQuery(ServerWebExchange exchange) {
        URI uri = exchange.getRequest().getURI();
        return uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
