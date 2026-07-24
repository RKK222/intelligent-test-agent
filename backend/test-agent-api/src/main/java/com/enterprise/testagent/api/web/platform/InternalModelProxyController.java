package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 仅供 opencode 子进程调用的内部 OpenAI-compatible 代理入口。
 */
@RestController
public class InternalModelProxyController {

    static final int MAX_REQUEST_BODY_BYTES = 2 * 1024 * 1024;

    private final InternalModelProxyForwardingService forwardingService;

    public InternalModelProxyController(InternalModelProxyForwardingService forwardingService) {
        this.forwardingService = Objects.requireNonNull(forwardingService, "forwardingService must not be null");
    }

    @RequestMapping("/api/internal/platform/opencode-runtime/internal-model-proxy/v1/**")
    public Mono<Void> proxy(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        long contentLength = exchange.getRequest().getHeaders().getContentLength();
        if (contentLength > MAX_REQUEST_BODY_BYTES) {
            return Mono.error(payloadTooLarge());
        }
        return readRequestBody(exchange)
                .flatMap(body -> forwardingService.forward(exchange, body, traceId));
    }

    /**
     * 仅为内部模型代理聚合请求体，避免放大全局 WebFlux codec 缓冲区；byte[] 直接转发，减少 String 副本。
     */
    private Mono<byte[]> readRequestBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody(), MAX_REQUEST_BODY_BYTES)
                .map(buffer -> {
                    try {
                        byte[] body = new byte[buffer.readableByteCount()];
                        buffer.read(body);
                        return body;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .onErrorMap(DataBufferLimitException.class, exception -> payloadTooLarge())
                .defaultIfEmpty(new byte[0]);
    }

    /**
     * 返回稳定的 413 平台错误，details 只暴露上限，不记录或回显模型请求内容。
     */
    private PlatformException payloadTooLarge() {
        return new PlatformException(
                ErrorCode.PAYLOAD_TOO_LARGE,
                "内部模型代理请求体超过 2 MiB 上限",
                Map.of("maxBytes", MAX_REQUEST_BODY_BYTES));
    }
}
