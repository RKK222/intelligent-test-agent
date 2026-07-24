package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionTaskApplicationService;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public NightExecutionController(NightExecutionTaskApplicationService service) {
        this.service = Objects.requireNonNull(service);
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
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        UserId userId = principal.userId();
        boolean superAdmin = AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        NightExecutionDtos.TaskResponse.from(
                                service.create(userId, superAdmin, request.toCommand(), traceId)),
                        traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping(BASE + "/tasks")
    public Mono<ApiResponse<NightExecutionDtos.TaskQueryResponse>> list(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        SessionId filter = sessionId == null || sessionId.isBlank() ? null : new SessionId(sessionId);
        return Mono.fromCallable(() -> ApiResponse.ok(NightExecutionDtos.TaskQueryResponse.from(
                        service.list(userId, filter, RuntimeApiSupport.pageRequest(page, size))), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping(BASE + "/tasks/{taskId}")
    public Mono<ApiResponse<NightExecutionDtos.TaskResponse>> adjust(
            @PathVariable String taskId,
            @Valid @RequestBody NightExecutionDtos.AdjustTaskRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        UserId userId = principal.userId();
        boolean superAdmin = AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN);
        return Mono.fromCallable(() -> ApiResponse.ok(NightExecutionDtos.TaskResponse.from(
                        service.adjust(userId, superAdmin, new NightExecutionTaskId(taskId),
                                request.slotStart(), traceId)), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(BASE + "/tasks/{taskId}/cancel")
    public Mono<ApiResponse<NightExecutionDtos.TaskResponse>> cancel(
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return Mono.fromCallable(() -> ApiResponse.ok(NightExecutionDtos.TaskResponse.from(
                        service.cancel(userId, new NightExecutionTaskId(taskId), traceId)), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(BASE + "/tasks/{taskId}/dismiss")
    public Mono<ApiResponse<NightExecutionDtos.TaskResponse>> dismiss(
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return Mono.fromCallable(() -> ApiResponse.ok(NightExecutionDtos.TaskResponse.from(
                        service.dismiss(userId, new NightExecutionTaskId(taskId), traceId)), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
