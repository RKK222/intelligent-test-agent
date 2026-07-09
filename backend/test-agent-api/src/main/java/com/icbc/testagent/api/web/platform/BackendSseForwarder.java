package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.observability.TraceConstants;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 后端 Java 之间的 SSE 流式转发器。
 *
 * <p>普通 `BackendHttpForwarder` 会把响应读成 byte[]，不能用于长期 `text/event-stream`；
 * 本转发器只处理 RunEvent SSE，保持响应体按 DataBuffer 流式写回浏览器。
 */
@Service
public class BackendSseForwarder {

    static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";

    private static final List<String> FORWARDED_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.ACCEPT,
            HttpHeaders.ACCEPT_LANGUAGE,
            TraceConstants.TRACE_ID_HEADER,
            LAST_EVENT_ID_HEADER);

    private final WebClient webClient;

    @Autowired
    public BackendSseForwarder(WebClient.Builder webClientBuilder) {
        this(webClientBuilder.build());
    }

    BackendSseForwarder(WebClient webClient) {
        this.webClient = Objects.requireNonNull(webClient, "webClient must not be null");
    }

    /**
     * 将当前 SSE 请求原样流式转发到目标 Java，并透传认证、trace、续传游标和 query。
     */
    Mono<Void> forward(ServerWebExchange exchange, BackendJavaProcess backend) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(backend, "backend must not be null");
        URI targetUri = URI.create(trimTrailingSlash(backend.listenUrl()) + pathAndQuery(exchange));
        return webClient.method(exchange.getRequest().getMethod())
                .uri(targetUri)
                .headers(headers -> copyForwardHeaders(exchange, headers))
                .exchangeToMono(response -> {
                    ServerHttpResponse targetResponse = exchange.getResponse();
                    targetResponse.setStatusCode(response.statusCode());
                    targetResponse.getHeaders().set("X-Accel-Buffering", "no");
                    targetResponse.getHeaders().setContentType(
                            response.headers().contentType().orElse(MediaType.TEXT_EVENT_STREAM));
                    response.headers().header(TraceConstants.TRACE_ID_HEADER).stream()
                            .findFirst()
                            .ifPresent(traceId -> targetResponse.getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId));
                    Flux<DataBuffer> body = response.bodyToFlux(DataBuffer.class);
                    return targetResponse.writeWith(body);
                })
                .onErrorMap(exception -> exception instanceof PlatformException
                        ? exception
                        : unavailable(backend, exception));
    }

    private void copyForwardHeaders(ServerWebExchange exchange, HttpHeaders headers) {
        for (String headerName : FORWARDED_HEADERS) {
            List<String> values = exchange.getRequest().getHeaders().get(headerName);
            if (values == null || values.isEmpty()) {
                continue;
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    headers.add(headerName, value);
                }
            }
        }
        if (!headers.containsHeader(HttpHeaders.ACCEPT)) {
            headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        }
        headers.set(BackendHttpForwarder.ROUTED_HEADER, "true");
        if (!headers.containsHeader(TraceConstants.TRACE_ID_HEADER)) {
            headers.set(TraceConstants.TRACE_ID_HEADER, RuntimeApiSupport.traceId(exchange));
        }
    }

    private PlatformException unavailable(BackendJavaProcess backend, Throwable cause) {
        return new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "目标服务器后端不可用",
                Map.of("linuxServerId", backend.linuxServerId().value()),
                cause);
    }

    private String pathAndQuery(ServerWebExchange exchange) {
        URI uri = exchange.getRequest().getURI();
        return uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
