package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.analytics.AiMessageFeedback;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.analytics.AiMessageFeedbackApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * AI 回复反馈 HTTP 入口，普通登录用户只能提交和查看自己的消息反馈。
 */
@RestController
@RequestMapping("/api/internal/platform/opencode-runtime/messages")
public class AiMessageFeedbackController {

    private final AiMessageFeedbackApplicationService service;

    public AiMessageFeedbackController(AiMessageFeedbackApplicationService service) {
        this.service = service;
    }

    @PutMapping("/{messageId}/feedback")
    public ApiResponse<AiFeedbackDtos.FeedbackResponse> putFeedback(
            @PathVariable String messageId,
            @RequestBody AiFeedbackDtos.FeedbackRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        AiMessageFeedback feedback = service.submitOrUpdate(
                userId,
                new SessionMessageId(messageId),
                request.rating(),
                request.reasonCode(),
                request.comment(),
                traceId);
        return ApiResponse.ok(AiFeedbackDtos.FeedbackResponse.from(feedback), traceId);
    }

    @GetMapping("/{messageId}/feedback/me")
    public ApiResponse<AiFeedbackDtos.FeedbackResponse> myFeedback(
            @PathVariable String messageId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ApiResponse.ok(
                service.findMyFeedback(userId, new SessionMessageId(messageId))
                        .map(AiFeedbackDtos.FeedbackResponse::from)
                        .orElse(null),
                traceId);
    }
}
