package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigMessageGate;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStatusQueryService;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessWeakHealthRequest;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessRestartService;
import com.enterprise.testagent.workspace.AgentConfigApplicationService;
import com.enterprise.testagent.workspace.AgentConfigResponses;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 当前登录用户的 TestAgent 进程状态与初始化入口。
 */
@RestController
public class UserOpencodeProcessController {

    private final UserOpencodeProcessAssignmentService processAssignmentService;
    private final OpencodeProcessStatusQueryService statusQueryService;
    private final AgentConfigApplicationService agentConfigService;
    private final UserOpencodeProcessRestartService processRestartService;
    private PublicAgentConfigMessageGate publicConfigMessageGate = ignored ->
            PublicAgentConfigMessageGate.MessageGateStatus.open();

    /**
     * 注入用户 TestAgent 进程分配服务，Controller 只负责协议适配和鉴权。
     */
    public UserOpencodeProcessController(
            UserOpencodeProcessAssignmentService processAssignmentService,
            OpencodeProcessStatusQueryService statusQueryService) {
        this(processAssignmentService, statusQueryService, null, null);
    }

    /** 兼容既有手工装配入口。 */
    public UserOpencodeProcessController(
            UserOpencodeProcessAssignmentService processAssignmentService,
            OpencodeProcessStatusQueryService statusQueryService,
            AgentConfigApplicationService agentConfigService) {
        this(processAssignmentService, statusQueryService, agentConfigService, null);
    }

    /** Spring 生产入口额外注入工作区和个人重启编排服务。 */
    @Autowired
    public UserOpencodeProcessController(
            UserOpencodeProcessAssignmentService processAssignmentService,
            OpencodeProcessStatusQueryService statusQueryService,
            AgentConfigApplicationService agentConfigService,
            UserOpencodeProcessRestartService processRestartService) {
        this.processAssignmentService = Objects.requireNonNull(processAssignmentService, "processAssignmentService must not be null");
        this.statusQueryService = Objects.requireNonNull(statusQueryService, "statusQueryService must not be null");
        this.agentConfigService = agentConfigService;
        this.processRestartService = processRestartService;
    }

    /** 注入持久化发布闸门，供强状态响应和独立轻量轮询接口复用。 */
    @Autowired(required = false)
    void configurePublicConfigMessageGate(PublicAgentConfigMessageGate messageGate) {
        this.publicConfigMessageGate = Objects.requireNonNull(messageGate, "messageGate must not be null");
    }

    /**
     * 查询当前用户 TestAgent 进程状态，不触发进程启动。
     */
    @GetMapping("/api/internal/agent/{agentId}/processes/me")
    public Mono<ApiResponse<RuntimeDtos.UserOpencodeProcessResponse>> status(
            @PathVariable String agentId,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return blockingResponse(exchange, traceId -> {
            PublicAgentConfigMessageGate.MessageGateStatus gate = publicConfigMessageGate.status(userId);
            return RuntimeDtos.UserOpencodeProcessResponse.from(
                    processAssignmentService.status(userId, agentId, traceId)
                            .withMessageGate(gate.allowed(), gate.reason(), gate.rolloutId()));
        });
    }

    /** 前端高频轮询只读取持久化发布闸门，不触发 manager health 或进程状态写回。 */
    @GetMapping("/api/internal/agent/{agentId}/processes/me/message-gate")
    public Mono<ApiResponse<RuntimeDtos.PublicConfigMessageGateResponse>> messageGate(
            @PathVariable String agentId,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        validateOpencodeAgent(agentId);
        return blockingResponse(exchange, traceId -> RuntimeDtos.PublicConfigMessageGateResponse.from(
                publicConfigMessageGate.status(userId)));
    }

    /**
     * 前端周期弱健康检查，只根据 Redis 快照定位本机 TestAgent 进程并直接访问 /global/health。
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
     * 初始化或重建当前用户 TestAgent 进程；真实启动由后续管理进程 gateway 完成。
     */
    @PostMapping("/api/internal/agent/{agentId}/processes/me/initialize")
    public Mono<ApiResponse<RuntimeDtos.UserOpencodeProcessResponse>> initialize(
            @PathVariable String agentId,
            @RequestBody(required = false) RuntimeDtos.UserOpencodeProcessInitializeRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        UserId userId = principal.userId();
        String operationId = request == null ? null : request.operationId();
        return blockingResponse(exchange, traceId -> {
            var process = processAssignmentService.initialize(userId, agentId, traceId, operationId);
            AgentConfigResponses.PublicWorktreePreparationResponse preparation = null;
            if (process.status() == UserOpencodeProcessAvailability.READY
                    && agentConfigService != null
                    && AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN)) {
                preparation = agentConfigService.preparePublicWorktreeForInitializedProcess(
                        process.linuxServerId(),
                        userId,
                        traceId);
            }
            return RuntimeDtos.UserOpencodeProcessResponse.from(process, preparation);
        });
    }

    /**
     * 重启当前用户 TestAgent 进程；活动 Run 的二次确认和排空均由目标 Java 上的应用服务权威判定。
     */
    @PostMapping("/api/internal/agent/{agentId}/processes/me/restart")
    public Mono<ApiResponse<RuntimeDtos.UserOpencodeProcessResponse>> restart(
            @PathVariable String agentId,
            @RequestBody(required = false) RuntimeDtos.UserOpencodeProcessRestartRequest request,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return blockingResponse(exchange, traceId -> {
            if (processRestartService == null) {
                throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "TestAgent 进程重启服务未配置");
            }
            boolean confirmRunning = request != null && request.confirmed();
            return RuntimeDtos.UserOpencodeProcessResponse.from(
                    processRestartService.restart(userId, agentId, confirmRunning, traceId));
        });
    }

    /**
     * 查询当前用户发起的 TestAgent 进程初始化进度，只读数据库快照。
     */
    @GetMapping("/api/internal/agent/{agentId}/processes/me/initialize-operations/{operationId}")
    public Mono<ApiResponse<RuntimeDtos.OpencodeProcessStartOperationResponse>> initializeOperation(
            @PathVariable String agentId,
            @PathVariable String operationId,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return blockingResponse(exchange, traceId -> RuntimeDtos.OpencodeProcessStartOperationResponse.from(
                processAssignmentService.findStartOperation(userId, agentId, operationId)
                        .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "TestAgent 进程初始化进度不存在"))));
    }

    private <T> Mono<ApiResponse<T>> blockingResponse(ServerWebExchange exchange, Function<String, T> action) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(action.apply(traceId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void validateOpencodeAgent(String agentId) {
        if (!"opencode".equals(agentId == null ? "" : agentId.trim().toLowerCase())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "当前只支持 TestAgent 用户进程");
        }
    }
}
