package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.run.CreateRunResendCommand;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.run.RunActorAttribution;
import com.enterprise.testagent.opencode.runtime.run.RunResendApplicationService;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 最后一条用户消息撤销重发入口；实际发送人、终态、远端边界与幂等均由统一应用服务复验。 */
@RestController
public class RunResendController {

    private final RunResendApplicationService resendService;
    private final RunApplicationService runService;
    private final SessionCollaborationShareService shareService;

    /** 兼容不涉及分享头的既有单元测试；生产装配必须使用包含分享服务的完整构造器。 */
    public RunResendController(
            RunResendApplicationService resendService,
            RunApplicationService runService) {
        this(resendService, runService, null);
    }

    /** 生产入口显式注入分享服务，确保分享成员的撤销重发先完成代操作授权。 */
    @Autowired
    public RunResendController(
            RunResendApplicationService resendService,
            RunApplicationService runService,
            SessionCollaborationShareService shareService) {
        this.resendService = Objects.requireNonNull(resendService);
        this.runService = Objects.requireNonNull(runService);
        this.shareService = shareService;
    }

    @PostMapping("/api/internal/agent/{agentId}/sessions/{sessionId}/resends")
    public Mono<ApiResponse<Response>> create(
            @PathVariable String agentId,
            @PathVariable String sessionId,
            @Valid @RequestBody Request request,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        var principal = AuthWebSupport.getAuthPrincipal(exchange);
        UserId actor = principal.userId();
        String traceId = RuntimeApiSupport.traceId(exchange);
        SessionId requestedSession = new SessionId(sessionId);
        DelegatedOperationContext context = shareContext(actor, shareId, traceId);
        return Mono.fromCallable(() -> {
                    CreateRunResendCommand command = new CreateRunResendCommand(
                            request.expectedRemoteMessageId(),
                            request.expectedRunId() == null ? null : new RunId(request.expectedRunId()),
                            request.contextToken(),
                            request.clientRequestId(),
                            request.editedPrompt());
                    RunResend resend = context == null
                            ? resendService.createManual(
                                    new RunActorAttribution(
                                            actor, actor, principal.unifiedAuthId(), false),
                                    agentId, requestedSession, command, traceId)
                            : resendService.createManual(
                                    context, agentId, requestedSession, command, traceId);
                    Function<UserId, String> usernameLookup = RuntimeDtos.memoizedUsernameLookup(
                            shareService == null ? null : shareService::findUsername);
                    return ApiResponse.ok(Response.from(
                            resend,
                            RuntimeDtos.RunResponse.from(
                                    runService.getRun(resend.replacementRunId()),
                                    null,
                                    null,
                                    null,
                                    resend,
                                    usernameLookup),
                            usernameLookup), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private DelegatedOperationContext shareContext(UserId actor, String shareId, String traceId) {
        if (shareId == null || shareId.isBlank()) return null;
        if (shareService == null) {
            throw new com.enterprise.testagent.common.error.PlatformException(
                    com.enterprise.testagent.common.error.ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                    "会话分享服务未配置");
        }
        return shareService.requireAccess(actor, new SessionShareId(shareId), true, traceId);
    }

    record Request(
            @NotBlank @Size(max = 128) String expectedRemoteMessageId,
            @Size(max = 128) String expectedRunId,
            @NotBlank @Size(max = 4096) String contextToken,
            @NotBlank @Size(max = 128) String clientRequestId,
            @Size(max = 20_000) String editedPrompt) {
    }

    record Response(
            String resendId,
            String status,
            Instant executeAt,
            RuntimeDtos.ResendMetadataResponse resend,
            RuntimeDtos.RunResponse replacementRun) {

        static Response from(RunResend resend, RuntimeDtos.RunResponse run) {
            return from(resend, run, null);
        }

        static Response from(
                RunResend resend,
                RuntimeDtos.RunResponse run,
                Function<UserId, String> usernameLookup) {
            return new Response(
                    resend.resendId().value(),
                    resend.status().name(),
                    resend.executeAt(),
                    RuntimeDtos.ResendMetadataResponse.from(resend, usernameLookup),
                    run);
        }
    }
}
