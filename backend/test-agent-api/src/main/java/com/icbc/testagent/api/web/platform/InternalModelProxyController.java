package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import java.util.Objects;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 仅供 opencode 子进程调用的内部 OpenAI-compatible 代理入口。
 */
@RestController
public class InternalModelProxyController {

    private final InternalModelProxyForwardingService forwardingService;

    public InternalModelProxyController(InternalModelProxyForwardingService forwardingService) {
        this.forwardingService = Objects.requireNonNull(forwardingService, "forwardingService must not be null");
    }

    @RequestMapping("/api/internal/platform/opencode-runtime/internal-model-proxy/v1/**")
    public Mono<Void> proxy(
            @RequestBody(required = false) String body,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return forwardingService.forward(exchange, body, traceId);
    }
}
