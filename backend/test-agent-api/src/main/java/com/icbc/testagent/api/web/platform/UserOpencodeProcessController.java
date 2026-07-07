package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessStatusQueryService;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessWeakHealthRequest;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 当前登录用户的 opencode 进程状态与初始化入口。
 */
@RestController
public class UserOpencodeProcessController {

    private final UserOpencodeProcessAssignmentService processAssignmentService;
    private final OpencodeProcessStatusQueryService statusQueryService;

    /**
     * 注入用户 opencode 进程分配服务，Controller 只负责协议适配和鉴权。
     */
    public UserOpencodeProcessController(
            UserOpencodeProcessAssignmentService processAssignmentService,
            OpencodeProcessStatusQueryService statusQueryService) {
        this.processAssignmentService = Objects.requireNonNull(processAssignmentService, "processAssignmentService must not be null");
        this.statusQueryService = Objects.requireNonNull(statusQueryService, "statusQueryService must not be null");
    }

    /**
     * 查询当前用户 opencode 进程状态，不触发进程启动。
     */
    @GetMapping("/api/internal/agent/{agentId}/processes/me")
    public Mono<ApiResponse<RuntimeDtos.UserOpencodeProcessResponse>> status(
            @PathVariable String agentId,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return blockingResponse(exchange, traceId -> RuntimeDtos.UserOpencodeProcessResponse.from(
                processAssignmentService.status(userId, agentId, traceId)));
    }

    /**
     * 前端周期弱健康检查，只根据 Redis 快照定位本机 opencode 进程并直接访问 /global/health。
     */
    @GetMapping("/api/internal/agent/{agentId}/processes/me/health")
    public Mono<ApiResponse<RuntimeDtos.UserOpencodeProcessHealthResponse>> health(
            @PathVariable String agentId,
            @RequestParam String linuxServerId,
            @RequestParam String containerId,
            @RequestParam int port,
            ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        return blockingResponse(exchange, traceId -> {
            validateOpencodeAgent(agentId);
            return RuntimeDtos.UserOpencodeProcessHealthResponse.from(statusQueryService.weakHealth(
                    new OpencodeProcessWeakHealthRequest(linuxServerId, containerId, port),
                    traceId));
        });
    }

    /**
     * 初始化或重建当前用户 opencode 进程；真实启动由后续管理进程 gateway 完成。
     */
    @PostMapping("/api/internal/agent/{agentId}/processes/me/initialize")
    public Mono<ApiResponse<RuntimeDtos.UserOpencodeProcessResponse>> initialize(
            @PathVariable String agentId,
            @RequestBody(required = false) RuntimeDtos.UserOpencodeProcessInitializeRequest request,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        String operationId = request == null ? null : request.operationId();
        return blockingResponse(exchange, traceId -> RuntimeDtos.UserOpencodeProcessResponse.from(
                processAssignmentService.initialize(userId, agentId, traceId, operationId)));
    }

    /**
     * 查询当前用户发起的 opencode 进程初始化进度，只读数据库快照。
     */
    @GetMapping("/api/internal/agent/{agentId}/processes/me/initialize-operations/{operationId}")
    public Mono<ApiResponse<RuntimeDtos.OpencodeProcessStartOperationResponse>> initializeOperation(
            @PathVariable String agentId,
            @PathVariable String operationId,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return blockingResponse(exchange, traceId -> RuntimeDtos.OpencodeProcessStartOperationResponse.from(
                processAssignmentService.findStartOperation(userId, agentId, operationId)
                        .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "opencode 进程初始化进度不存在"))));
    }

    private <T> Mono<ApiResponse<T>> blockingResponse(ServerWebExchange exchange, Function<String, T> action) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(action.apply(traceId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void validateOpencodeAgent(String agentId) {
        if (!"opencode".equals(agentId == null ? "" : agentId.trim().toLowerCase())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "当前只支持 opencode 用户进程");
        }
    }
}
