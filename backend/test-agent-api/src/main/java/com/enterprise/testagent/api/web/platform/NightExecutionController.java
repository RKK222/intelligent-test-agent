package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionTaskApplicationService;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 当前用户夜间任务 HTTP 入口；owner 和来源始终取服务端认证上下文。 */
@RestController
public class NightExecutionController {

    private static final String BASE = "/api/internal/platform/opencode-runtime/night-execution";
    private final NightExecutionTaskApplicationService service;
    private final SessionCollaborationShareService shareService;

    public NightExecutionController(NightExecutionTaskApplicationService service) {
        this(service, null);
    }

    @Autowired
    public NightExecutionController(
            NightExecutionTaskApplicationService service,
            SessionCollaborationShareService shareService) {
        this.service = Objects.requireNonNull(service);
        this.shareService = shareService;
    }

    @GetMapping(BASE + "/slots")
    public Mono<ApiResponse<NightExecutionDtos.SlotsResponse>> slots(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        AuthWebSupport.getAuthPrincipal(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(NightExecutionDtos.SlotsResponse.from(service.slots()), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(BASE + "/tasks")
    public Mono<ApiResponse<NightExecutionDtos.TaskResponse>> create(
            @Valid @RequestBody NightExecutionDtos.CreateTaskRequest request,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        UserId userId = principal.userId();
        boolean superAdmin = AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN);
        DelegatedOperationContext context = shareContext(userId, shareId, true, traceId);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        NightExecutionDtos.TaskResponse.from(
                                context == null
                                        ? service.create(
                                                userId, principal.unifiedAuthId(), superAdmin,
                                                request.toCommand(), traceId)
                                        : service.create(context, superAdmin, request.toCommand(), traceId),
                                usernameLookup()),
                        traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping(BASE + "/tasks")
    public Mono<ApiResponse<NightExecutionDtos.TaskQueryResponse>> list(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        SessionId filter = sessionId == null || sessionId.isBlank() ? null : new SessionId(sessionId);
        DelegatedOperationContext context = shareContext(userId, shareId, false, traceId);
        return Mono.fromCallable(() -> ApiResponse.ok(NightExecutionDtos.TaskQueryResponse.from(
                        context == null
                                ? service.list(userId, filter, RuntimeApiSupport.pageRequest(page, size))
                                : service.list(context, filter, RuntimeApiSupport.pageRequest(page, size)),
                        usernameLookup()), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping(BASE + "/tasks/{taskId}")
    public Mono<ApiResponse<NightExecutionDtos.TaskResponse>> adjust(
            @PathVariable String taskId,
            @Valid @RequestBody NightExecutionDtos.AdjustTaskRequest request,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        UserId userId = principal.userId();
        boolean superAdmin = AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN);
        DelegatedOperationContext context = shareContext(userId, shareId, true, traceId);
        return Mono.fromCallable(() -> ApiResponse.ok(NightExecutionDtos.TaskResponse.from(
                        context == null
                                ? service.adjust(userId, superAdmin, new NightExecutionTaskId(taskId),
                                        request.slotStart(), traceId)
                                : service.adjust(context, superAdmin, new NightExecutionTaskId(taskId),
                                        request.slotStart(), traceId),
                        usernameLookup()), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(BASE + "/tasks/{taskId}/cancel")
    public Mono<ApiResponse<NightExecutionDtos.TaskResponse>> cancel(
            @PathVariable String taskId,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        DelegatedOperationContext context = shareContext(userId, shareId, false, traceId);
        return Mono.fromCallable(() -> ApiResponse.ok(NightExecutionDtos.TaskResponse.from(
                        context == null
                                ? service.cancel(userId, new NightExecutionTaskId(taskId), traceId)
                                : service.cancel(context, new NightExecutionTaskId(taskId), traceId),
                        usernameLookup()), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(BASE + "/tasks/{taskId}/dismiss")
    public Mono<ApiResponse<NightExecutionDtos.TaskResponse>> dismiss(
            @PathVariable String taskId,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        DelegatedOperationContext context = shareContext(userId, shareId, false, traceId);
        return Mono.fromCallable(() -> ApiResponse.ok(NightExecutionDtos.TaskResponse.from(
                        context == null
                                ? service.dismiss(userId, new NightExecutionTaskId(taskId), traceId)
                                : service.dismiss(context, new NightExecutionTaskId(taskId), traceId),
                        usernameLookup()), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private DelegatedOperationContext shareContext(
            UserId actor,
            String shareId,
            boolean requireChat,
            String traceId) {
        if (shareId == null || shareId.isBlank()) return null;
        if (shareService == null) {
            throw new com.enterprise.testagent.common.error.PlatformException(
                    com.enterprise.testagent.common.error.ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                    "会话分享服务未配置");
        }
        return shareService.requireAccess(actor, new SessionShareId(shareId), requireChat, traceId);
    }

    private Function<UserId, String> usernameLookup() {
        return RuntimeDtos.memoizedUsernameLookup(
                shareService == null ? null : shareService::findUsername);
    }
}
