package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.run.CreateRunResendCommand;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.run.RunResendApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 最后一条用户消息撤销重发入口；owner、终态、远端边界与幂等均由统一应用服务复验。 */
@RestController
public class RunResendController {

    private final RunResendApplicationService resendService;
    private final RunApplicationService runService;

    public RunResendController(
            RunResendApplicationService resendService,
            RunApplicationService runService) {
        this.resendService = Objects.requireNonNull(resendService);
        this.runService = Objects.requireNonNull(runService);
    }

    @PostMapping("/api/internal/agent/{agentId}/sessions/{sessionId}/resends")
    public Mono<ApiResponse<Response>> create(
            @PathVariable String agentId,
            @PathVariable String sessionId,
            @Valid @RequestBody Request request,
            ServerWebExchange exchange) {
        UserId owner = AuthWebSupport.getAuthPrincipal(exchange).userId();
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> {
                    RunResend resend = resendService.createManual(
                            owner,
                            agentId,
                            new SessionId(sessionId),
                            new CreateRunResendCommand(
                                    request.expectedRemoteMessageId(),
                                    request.expectedRunId() == null ? null : new RunId(request.expectedRunId()),
                                    request.contextToken(),
                                    request.clientRequestId()),
                            traceId);
                    return ApiResponse.ok(Response.from(
                            resend,
                            RuntimeDtos.RunResponse.from(
                                    runService.getRun(resend.replacementRunId()),
                                    null,
                                    null,
                                    null,
                                    resend)), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    record Request(
            @NotBlank @Size(max = 128) String expectedRemoteMessageId,
            @Size(max = 128) String expectedRunId,
            @NotBlank @Size(max = 4096) String contextToken,
            @NotBlank @Size(max = 128) String clientRequestId) {
    }

    record Response(
            String resendId,
            String status,
            Instant executeAt,
            RuntimeDtos.ResendMetadataResponse resend,
            RuntimeDtos.RunResponse replacementRun) {

        static Response from(RunResend resend, RuntimeDtos.RunResponse run) {
            return new Response(
                    resend.resendId().value(),
                    resend.status().name(),
                    resend.executeAt(),
                    RuntimeDtos.ResendMetadataResponse.from(resend),
                    run);
        }
    }
}
