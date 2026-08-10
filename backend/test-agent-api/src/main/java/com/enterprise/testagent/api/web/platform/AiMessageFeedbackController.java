package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.analytics.AiMessageFeedback;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.analytics.AiMessageFeedbackApplicationService;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * AI 回复反馈 HTTP 入口，普通登录用户只能提交和查看自己的消息反馈。
 */
@RestController
@RequestMapping("/api/internal/platform/opencode-runtime/messages")
public class AiMessageFeedbackController {

    private final AiMessageFeedbackApplicationService service;
    private final SessionCollaborationShareService shareService;

    public AiMessageFeedbackController(AiMessageFeedbackApplicationService service) {
        this(service, null);
    }

    @Autowired
    public AiMessageFeedbackController(
            AiMessageFeedbackApplicationService service,
            SessionCollaborationShareService shareService) {
        this.service = service;
        this.shareService = shareService;
    }

    @PutMapping("/{messageId}/feedback")
    public ApiResponse<AiFeedbackDtos.FeedbackResponse> putFeedback(
            @PathVariable String messageId,
            @RequestBody AiFeedbackDtos.FeedbackRequest request,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        SessionMessageId currentMessageId = new SessionMessageId(messageId);
        FeedbackActor actor = feedbackActor(exchange, shareId, currentMessageId, true, traceId);
        try {
            AiMessageFeedback feedback = service.submitOrUpdate(
                    actor.executionUserId(), currentMessageId, request.rating(), request.reasonCode(),
                    request.comment(), traceId);
            audit(actor.context(), "MESSAGE_FEEDBACK_UPDATED", currentMessageId.value(), "SUCCESS", null, traceId);
            return ApiResponse.ok(AiFeedbackDtos.FeedbackResponse.from(feedback), traceId);
        } catch (RuntimeException failure) {
            audit(actor.context(), "MESSAGE_FEEDBACK_UPDATED", currentMessageId.value(), "FAILED", errorCode(failure), traceId);
            throw failure;
        }
    }

    @GetMapping("/{messageId}/feedback/me")
    public ApiResponse<AiFeedbackDtos.FeedbackResponse> myFeedback(
            @PathVariable String messageId,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        SessionMessageId currentMessageId = new SessionMessageId(messageId);
        FeedbackActor actor = feedbackActor(exchange, shareId, currentMessageId, false, traceId);
        try {
            var response = service.findMyFeedback(actor.executionUserId(), currentMessageId)
                    .map(AiFeedbackDtos.FeedbackResponse::from).orElse(null);
            audit(actor.context(), "MESSAGE_FEEDBACK_READ", currentMessageId.value(), "SUCCESS", null, traceId);
            return ApiResponse.ok(response, traceId);
        } catch (RuntimeException failure) {
            audit(actor.context(), "MESSAGE_FEEDBACK_READ", currentMessageId.value(), "FAILED", errorCode(failure), traceId);
            throw failure;
        }
    }

    /** 分享头只改变显式执行身份，真实登录主体仍保留在 AuthPrincipal 中。 */
    private FeedbackActor feedbackActor(
            ServerWebExchange exchange,
            String shareId,
            SessionMessageId messageId,
            boolean requireChat,
            String traceId) {
        UserId authenticatedActor = AuthWebSupport.getAuthPrincipal(exchange).userId();
        if (shareId == null || shareId.isBlank()) {
            return new FeedbackActor(authenticatedActor, null);
        }
        if (shareService == null) {
            throw new IllegalStateException("会话分享服务未配置");
        }
        DelegatedOperationContext context = shareService.requireAccess(
                authenticatedActor, new SessionShareId(shareId), requireChat, traceId);
        context.requireSession(service.requireMessageSessionId(messageId));
        return new FeedbackActor(context.executionOwnerUserId(), context);
    }

    private void audit(
            DelegatedOperationContext context,
            String action,
            String messageId,
            String outcome,
            String errorCode,
            String traceId) {
        if (context != null) {
            shareService.recordOperation(
                    context, action, "MESSAGE", messageId, null, outcome, errorCode, traceId);
        }
    }

    private String errorCode(RuntimeException failure) {
        return failure instanceof PlatformException platform
                ? platform.errorCode().name()
                : "INTERNAL_ERROR";
    }

    private record FeedbackActor(UserId executionUserId, DelegatedOperationContext context) {
    }
}
