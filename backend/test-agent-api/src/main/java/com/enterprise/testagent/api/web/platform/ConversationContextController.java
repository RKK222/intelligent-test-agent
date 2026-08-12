package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.run.ConversationContextApplicationService;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final SessionCollaborationShareService shareService;

    /**
     * 兼容既有测试和手工装配；普通会话不需要分享服务。
     */
    public ConversationContextController(ConversationContextApplicationService contextService) {
        this(contextService, null);
    }

    /** 多构造器场景必须显式标注生产构造器，避免 Spring 回退查找无参构造器。 */
    @Autowired
    public ConversationContextController(
            ConversationContextApplicationService contextService,
            SessionCollaborationShareService shareService) {
        this.contextService = Objects.requireNonNull(contextService, "contextService must not be null");
        this.shareService = shareService;
    }

    /**
     * 为当前认证用户首次加载会话所需的后续 Run 上下文签发 opaque token。
     */
    @PostMapping("/api/internal/agent/{agentId}/sessions/{sessionId}/run-context")
    public Mono<ApiResponse<ConversationContextResponse>> bootstrap(
            @PathVariable("agentId") String agentId,
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        UserId actorUserId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
                    SessionId requestedSessionId = new SessionId(sessionId);
                    UserId executionUserId = executionUserId(
                            actorUserId, shareId, requestedSessionId, traceId);
                    ConversationContextApplicationService.IssuedConversationContext issued = contextService.bootstrap(
                            executionUserId,
                            agentId,
                            requestedSessionId,
                            traceId);
                    return ApiResponse.ok(ConversationContextResponse.from(issued), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 分享成员只获得当前会话的所属人执行身份，真实认证主体始终保留在 exchange 中。 */
    private UserId executionUserId(
            UserId actorUserId,
            String shareId,
            SessionId sessionId,
            String traceId) {
        if (shareId == null || shareId.isBlank()) {
            return actorUserId;
        }
        if (shareService == null) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "会话分享服务未配置");
        }
        DelegatedOperationContext context = shareService.requireAccess(
                actorUserId, new SessionShareId(shareId), true, traceId);
        context.requireSession(sessionId);
        return context.executionOwnerUserId();
    }

    /** 对外返回冻结目标标识，但不泄露用户、根路径、服务端进程或模型授权。 */
    record ConversationContextResponse(
            String contextToken,
            int contextVersion,
            Instant expiresAt,
            String runtimeKind,
            String localClientInstanceId,
            Long connectionGeneration) {

        static ConversationContextResponse from(
                ConversationContextApplicationService.IssuedConversationContext issued) {
            return new ConversationContextResponse(
                    issued.contextToken(),
                    issued.context().contextVersion(),
                    issued.context().expiresAt(),
                    issued.context().runtimeKind().name(),
                    issued.context().localClientInstanceId(),
                    issued.context().connectionGeneration());
        }
    }
}
