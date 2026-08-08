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
        return Mono.defer(() -> {
            // 鉴权和供应商快照解析必须先于请求体订阅，避免无效请求占用 2 MiB 聚合缓冲区。
            InternalModelProxyForwardingService.PreparedRequest preparedRequest;
            try {
                preparedRequest = forwardingService.prepareRequest(exchange);
            } catch (PlatformException exception) {
                forwardingService.recordPrepareRequestFailure(exchange, traceId, exception);
                throw exception;
            }
            long contentLength = exchange.getRequest().getHeaders().getContentLength();
            if (contentLength > MAX_REQUEST_BODY_BYTES) {
                PlatformException exception = payloadTooLarge();
                forwardingService.recordRequestValidationFailure(exchange, traceId, exception);
                return Mono.error(exception);
            }
            return readRequestBody(exchange)
                    .flatMap(body -> forwardBody(exchange, body, traceId, preparedRequest));
        });
    }

    /**
     * 转发请求体；同步校验失败（缺 model、非法 JSON、responses 转换）记录后原样抛出。
     */
    private Mono<Void> forwardBody(
            ServerWebExchange exchange,
            byte[] body,
            String traceId,
            InternalModelProxyForwardingService.PreparedRequest preparedRequest) {
        try {
            return forwardingService.forward(exchange, body, traceId, preparedRequest);
        } catch (PlatformException exception) {
            forwardingService.recordRequestValidationFailure(exchange, traceId, exception);
            throw exception;
        }
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
