package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.analytics.AiRunFeedback;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.analytics.AiRunFeedbackApplicationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 当前登录用户对主智能体 Run 整体回复的评价入口。 */
@RestController
@RequestMapping("/api/internal/platform/opencode-runtime")
public class AiRunFeedbackController {

    private final AiRunFeedbackApplicationService service;

    public AiRunFeedbackController(AiRunFeedbackApplicationService service) {
        this.service = service;
    }

    @PutMapping("/runs/{runId}/feedback")
    public ApiResponse<AiRunFeedbackDtos.FeedbackResponse> putFeedback(
            @PathVariable String runId,
            @RequestBody AiRunFeedbackDtos.FeedbackRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        AiRunFeedback feedback = service.submitOrUpdate(
                userId, new RunId(runId), request.rating(), request.reasonCode(), request.comment(), traceId);
        return ApiResponse.ok(AiRunFeedbackDtos.FeedbackResponse.from(feedback), traceId);
    }

    @GetMapping("/runs/{runId}/feedback/me")
    public ApiResponse<AiRunFeedbackDtos.FeedbackResponse> myFeedback(
            @PathVariable String runId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return ApiResponse.ok(
                service.findMyFeedback(userId, new RunId(runId))
                        .map(AiRunFeedbackDtos.FeedbackResponse::from).orElse(null), traceId);
    }

    @PostMapping("/run-feedbacks/me/query")
    public ApiResponse<List<AiRunFeedbackDtos.FeedbackStateResponse>> queryMyFeedbacks(
            @RequestBody AiRunFeedbackDtos.FeedbackQueryRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        List<RunId> runIds = request.runIds() == null
                ? List.of()
                : request.runIds().stream().map(RunId::new).toList();
        return ApiResponse.ok(
                service.findMyFeedbackStates(userId, runIds).stream()
                        .map(AiRunFeedbackDtos.FeedbackStateResponse::from).toList(), traceId);
    }
}
