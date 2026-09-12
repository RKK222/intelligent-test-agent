package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.integration.codeknowledge.TraceWeaveCodeKnowledgeSettings;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 为 Mimo 工作台返回当前认证用户可选择的最小代码知识范围。 */
@RestController
public class CodeKnowledgeScopeController {

    public static final String SCOPE_ENDPOINT_PATH = "/api/internal/platform/code-knowledge/scope";

    private final TraceWeaveCodeKnowledgeSettings settings;

    public CodeKnowledgeScopeController(TraceWeaveCodeKnowledgeSettings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    @GetMapping(SCOPE_ENDPOINT_PATH)
    public Mono<ApiResponse<TraceWeaveCodeKnowledgeSettings.SelectionScope>> scope(
            ServerWebExchange exchange) {
        var userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(settings.selectionScope(userId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
