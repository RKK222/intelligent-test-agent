package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.analytics.AiRunFeedback;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.analytics.AiRunFeedbackApplicationService;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 当前登录用户对主智能体 Run 整体回复的评价入口。 */
@RestController
@RequestMapping("/api/internal/platform/opencode-runtime")
public class AiRunFeedbackController {

    private final AiRunFeedbackApplicationService service;
    private final SessionCollaborationShareService shareService;
    private final RunApplicationService runService;

    public AiRunFeedbackController(AiRunFeedbackApplicationService service) {
        this(service, null, null);
    }

    @Autowired
    public AiRunFeedbackController(
            AiRunFeedbackApplicationService service,
            SessionCollaborationShareService shareService,
            RunApplicationService runService) {
        this.service = service;
        this.shareService = shareService;
        this.runService = runService;
    }

    @PutMapping("/runs/{runId}/feedback")
    public ApiResponse<AiRunFeedbackDtos.FeedbackResponse> putFeedback(
            @PathVariable String runId,
            @RequestBody AiRunFeedbackDtos.FeedbackRequest request,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        RunId currentRunId = new RunId(runId);
        FeedbackActor actor = feedbackActor(exchange, shareId, currentRunId, true, traceId);
        try {
            AiRunFeedback feedback = service.submitOrUpdate(
                    actor.executionUserId(), currentRunId, request.rating(), request.reasonCode(),
                    request.comment(), traceId);
            audit(actor.context(), "RUN_FEEDBACK_UPDATED", currentRunId.value(), "SUCCESS", null, traceId);
            return ApiResponse.ok(AiRunFeedbackDtos.FeedbackResponse.from(feedback), traceId);
        } catch (RuntimeException failure) {
            audit(actor.context(), "RUN_FEEDBACK_UPDATED", currentRunId.value(), "FAILED", errorCode(failure), traceId);
            throw failure;
        }
    }

    @GetMapping("/runs/{runId}/feedback/me")
    public ApiResponse<AiRunFeedbackDtos.FeedbackResponse> myFeedback(
            @PathVariable String runId,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        RunId currentRunId = new RunId(runId);
        FeedbackActor actor = feedbackActor(exchange, shareId, currentRunId, false, traceId);
        try {
            var response = service.findMyFeedback(actor.executionUserId(), currentRunId)
                    .map(AiRunFeedbackDtos.FeedbackResponse::from).orElse(null);
            audit(actor.context(), "RUN_FEEDBACK_READ", currentRunId.value(), "SUCCESS", null, traceId);
            return ApiResponse.ok(response, traceId);
        } catch (RuntimeException failure) {
            audit(actor.context(), "RUN_FEEDBACK_READ", currentRunId.value(), "FAILED", errorCode(failure), traceId);
            throw failure;
        }
    }

    @PostMapping("/run-feedbacks/me/query")
    public ApiResponse<List<AiRunFeedbackDtos.FeedbackStateResponse>> queryMyFeedbacks(
            @RequestBody AiRunFeedbackDtos.FeedbackQueryRequest request,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        List<RunId> runIds = request.runIds() == null
                ? List.of()
                : request.runIds().stream().map(RunId::new).toList();
        FeedbackActor actor = feedbackActor(exchange, shareId, runIds, traceId);
        try {
            var response = service.findMyFeedbackStates(actor.executionUserId(), runIds).stream()
                    .map(AiRunFeedbackDtos.FeedbackStateResponse::from).toList();
            audit(actor.context(), "RUN_FEEDBACK_BATCH_READ", null, "SUCCESS", null, traceId);
            return ApiResponse.ok(response, traceId);
        } catch (RuntimeException failure) {
            audit(actor.context(), "RUN_FEEDBACK_BATCH_READ", null, "FAILED", errorCode(failure), traceId);
            throw failure;
        }
    }

    private FeedbackActor feedbackActor(
            ServerWebExchange exchange,
            String shareId,
            RunId runId,
            boolean requireChat,
            String traceId) {
        FeedbackActor actor = feedbackActor(exchange, shareId, requireChat, traceId);
        if (actor.context() != null) {
            runService.requireRunAccess(actor.context(), runId);
        }
        return actor;
    }

    private FeedbackActor feedbackActor(
            ServerWebExchange exchange,
            String shareId,
            List<RunId> runIds,
            String traceId) {
        FeedbackActor actor = feedbackActor(exchange, shareId, false, traceId);
        if (actor.context() != null) {
            runIds.forEach(runId -> runService.requireRunAccess(actor.context(), runId));
        }
        return actor;
    }

    /** 分享头只建立代操作上下文，真实认证主体始终保留在 ServerWebExchange。 */
    private FeedbackActor feedbackActor(
            ServerWebExchange exchange,
            String shareId,
            boolean requireChat,
            String traceId) {
        UserId authenticatedActor = AuthWebSupport.getAuthPrincipal(exchange).userId();
        if (shareId == null || shareId.isBlank()) {
            return new FeedbackActor(authenticatedActor, null);
        }
        if (shareService == null || runService == null) {
            throw new IllegalStateException("会话分享服务未配置");
        }
        DelegatedOperationContext context = shareService.requireAccess(
                authenticatedActor, new SessionShareId(shareId), requireChat, traceId);
        return new FeedbackActor(context.executionOwnerUserId(), context);
    }

    private void audit(
            DelegatedOperationContext context,
            String action,
            String runId,
            String outcome,
            String errorCode,
            String traceId) {
        if (context != null) {
            shareService.recordOperation(
                    context, action, "RUN", runId, null, outcome, errorCode, traceId);
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
