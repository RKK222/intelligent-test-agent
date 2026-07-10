package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.run.ConversationContextApplicationService;
import java.time.Instant;
import java.util.Objects;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 会话运行上下文 HTTP 入口，只负责认证主体、路径参数和响应 DTO 转换。
 */
@RestController
public class ConversationContextController {

    private final ConversationContextApplicationService contextService;

    /**
     * 注入运行上下文应用服务。
     */
    public ConversationContextController(ConversationContextApplicationService contextService) {
        this.contextService = Objects.requireNonNull(contextService, "contextService must not be null");
    }

    /**
     * 为当前认证用户首次加载会话所需的后续 Run 上下文签发 opaque token。
     */
    @PostMapping("/api/internal/agent/{agentId}/sessions/{sessionId}/run-context")
    public Mono<ApiResponse<ConversationContextResponse>> bootstrap(
            @PathVariable("agentId") String agentId,
            @PathVariable("sessionId") String sessionId,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
                    ConversationContextApplicationService.IssuedConversationContext issued = contextService.bootstrap(
                            userId,
                            agentId,
                            new SessionId(sessionId),
                            traceId);
                    return ApiResponse.ok(ConversationContextResponse.from(issued), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 对外仅返回 token、版本和过期时间，不泄露用户、路径、进程或节点绑定。
     */
    record ConversationContextResponse(String contextToken, int contextVersion, Instant expiresAt) {

        static ConversationContextResponse from(
                ConversationContextApplicationService.IssuedConversationContext issued) {
            return new ConversationContextResponse(
                    issued.contextToken(),
                    issued.context().contextVersion(),
                    issued.context().expiresAt());
        }
    }
}
