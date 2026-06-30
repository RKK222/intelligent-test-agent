package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessControlResult;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementCommandService;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementQueryService;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 超级管理员运行管理只读 API，展示 opencode 运行拓扑和用户进程状态。
 */
@RestController
public class RuntimeManagementController {

    private static final Duration DEFAULT_METRIC_WINDOW = Duration.ofMinutes(60);
    private static final int MAX_METRIC_HOURS = 48;
    private static final Set<Integer> ALLOWED_METRIC_WINDOW_MINUTES = Set.of(1, 30, 60, 360, 720, 1440, 2880);
    private static final int DEFAULT_METRIC_POINTS = 720;
    private static final int MAX_METRIC_POINTS = 2000;

    private final RuntimeManagementQueryService queryService;
    private final RuntimeManagementCommandService commandService;
    private final RuntimeManagementBackendRoutingService backendRoutingService;

    /**
     * 注入运行管理查询和命令服务；Controller 不直接访问 Repository。
     */
    @Autowired
    public RuntimeManagementController(
            RuntimeManagementQueryService queryService,
            RuntimeManagementCommandService commandService,
            RuntimeManagementBackendRoutingService backendRoutingService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
        this.commandService = Objects.requireNonNull(commandService, "commandService must not be null");
        this.backendRoutingService = Objects.requireNonNull(backendRoutingService, "backendRoutingService must not be null");
    }

    /**
     * 查询运行管理概览，只有 SUPER_ADMIN 可访问。
     */
    @GetMapping("/api/internal/platform/opencode-runtime/management/overview")
    public Mono<ApiResponse<RuntimeManagementDtos.OverviewResponse>> overview(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String linuxServerId,
            @RequestParam(required = false) String containerId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        PageRequest pageRequest = RuntimeApiSupport.pageRequest(page, size);
        OpencodeServerProcessFilter filter = new OpencodeServerProcessFilter(
                parseStatus(status),
                parseLinuxServerId(linuxServerId),
                parseContainerId(containerId),
                parseUserId(userId),
                parseUsername(username));
        return Mono.fromCallable(() -> ApiResponse.ok(
                        RuntimeManagementDtos.OverviewResponse.from(queryService.overview(filter, pageRequest, traceId)),
                        traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 按用户关键字查询 opencode server 进程，并返回 manager 主动探测后的实际状态。
     */
    @GetMapping("/api/internal/platform/opencode-runtime/management/user-processes")
    public Mono<ApiResponse<com.icbc.testagent.common.pagination.PageResponse<RuntimeManagementDtos.OpencodeProcessResponse>>> userProcesses(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        PageRequest pageRequest = RuntimeApiSupport.pageRequest(page, size);
        return Mono.fromCallable(() -> {
                    var result = queryService.userProcesses(keyword, pageRequest, traceId);
                    return ApiResponse.ok(RuntimeManagementDtos.opencodeProcessPage(result), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 重启指定容器端口上的 opencode server。
     */
    @PostMapping("/api/internal/platform/opencode-runtime/management/containers/{containerId}/processes/{port}/restart")
    public Mono<ApiResponse<RuntimeManagementDtos.ManagedProcessCommandResponse>> restartManagedProcess(
            @PathVariable String containerId,
            @PathVariable String port,
            ServerWebExchange exchange) {
        return controlManagedProcess(containerId, port, exchange, true);
    }

    /**
     * 停止指定容器端口上的 opencode server。
     */
    @PostMapping("/api/internal/platform/opencode-runtime/management/containers/{containerId}/processes/{port}/stop")
    public Mono<ApiResponse<RuntimeManagementDtos.ManagedProcessCommandResponse>> stopManagedProcess(
            @PathVariable String containerId,
            @PathVariable String port,
            ServerWebExchange exchange) {
        return controlManagedProcess(containerId, port, exchange, false);
    }

    /**
     * 查询单个容器近 48 小时内的 Redis 指标历史。
     */
    @GetMapping("/api/internal/platform/opencode-runtime/management/containers/{containerId}/metrics")
    public Mono<ApiResponse<RuntimeManagementDtos.ContainerMetricHistoryResponse>> containerMetrics(
            @PathVariable String containerId,
            @RequestParam(required = false) Integer windowMinutes,
            @RequestParam(required = false) Integer hours,
            @RequestParam(required = false) Integer maxPoints,
            ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        OpencodeContainerId parsedContainerId = parseContainerId(containerId);
        Duration resolvedWindow = parseMetricWindow(windowMinutes, hours);
        int resolvedMaxPoints = parseMetricMaxPoints(maxPoints);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        RuntimeManagementDtos.ContainerMetricHistoryResponse.from(queryService.containerMetrics(
                                parsedContainerId,
                                resolvedWindow,
                                resolvedMaxPoints,
                                traceId)),
                        traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 按服务器 IP 查询后端 Java 服务近 48 小时内的 Redis 指标历史。
     */
    @GetMapping("/api/internal/platform/opencode-runtime/management/linux-servers/{linuxServerId}/backend-metrics")
    public Mono<ApiResponse<RuntimeManagementDtos.BackendMetricHistoryResponse>> backendServerMetrics(
            @PathVariable String linuxServerId,
            @RequestParam(required = false) Integer windowMinutes,
            @RequestParam(required = false) Integer hours,
            @RequestParam(required = false) Integer maxPoints,
            ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        LinuxServerId parsedLinuxServerId = parseLinuxServerId(linuxServerId);
        Duration resolvedWindow = parseMetricWindow(windowMinutes, hours);
        int resolvedMaxPoints = parseMetricMaxPoints(maxPoints);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        RuntimeManagementDtos.BackendMetricHistoryResponse.from(queryService.backendServerMetrics(
                                parsedLinuxServerId,
                                resolvedWindow,
                                resolvedMaxPoints,
                                traceId)),
                        traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 查询单个后端 Java 进程近 48 小时内的 Redis 指标历史，保留给旧客户端兼容。
     */
    @GetMapping("/api/internal/platform/opencode-runtime/management/backend-processes/{backendProcessId}/metrics")
    public Mono<ApiResponse<RuntimeManagementDtos.BackendMetricHistoryResponse>> backendProcessMetrics(
            @PathVariable String backendProcessId,
            @RequestParam(required = false) Integer windowMinutes,
            @RequestParam(required = false) Integer hours,
            @RequestParam(required = false) Integer maxPoints,
            ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        BackendProcessId parsedBackendProcessId = parseBackendProcessId(backendProcessId);
        Duration resolvedWindow = parseMetricWindow(windowMinutes, hours);
        int resolvedMaxPoints = parseMetricMaxPoints(maxPoints);
        return Mono.fromCallable(() -> ApiResponse.ok(
                        RuntimeManagementDtos.BackendMetricHistoryResponse.from(queryService.backendProcessMetrics(
                                parsedBackendProcessId,
                                resolvedWindow,
                                resolvedMaxPoints,
                                traceId)),
                        traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<ApiResponse<RuntimeManagementDtos.ManagedProcessCommandResponse>> controlManagedProcess(
            String containerId,
            String port,
            ServerWebExchange exchange,
            boolean restart) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        OpencodeContainerId parsedContainerId = parseRequiredContainerId(containerId);
        int parsedPort = parsePort(port);
        return Mono.fromCallable(() -> {
                    var forwardTarget = backendRoutingService.forwardTargetForContainer(exchange, parsedContainerId);
                    if (forwardTarget.isPresent()) {
                        return backendRoutingService.forward(
                                exchange,
                                forwardTarget.get(),
                                new TypeReference<ApiResponse<RuntimeManagementDtos.ManagedProcessCommandResponse>>() {});
                    }
                    OpencodeProcessControlResult result = restart
                            ? commandService.restartManagedProcess(parsedContainerId, parsedPort, traceId)
                            : commandService.stopManagedProcess(parsedContainerId, parsedPort, traceId);
                    return ApiResponse.ok(RuntimeManagementDtos.ManagedProcessCommandResponse.from(result), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private OpencodeServerProcessStatus parseStatus(String rawStatus) {
        String value = textOrNull(rawStatus);
        if (value == null) {
            return null;
        }
        try {
            return OpencodeServerProcessStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "进程状态无效",
                    Map.of("status", value),
                    exception);
        }
    }

    private LinuxServerId parseLinuxServerId(String rawLinuxServerId) {
        String value = textOrNull(rawLinuxServerId);
        if (value == null) {
            return null;
        }
        try {
            return new LinuxServerId(value);
        } catch (IllegalArgumentException exception) {
            throw validationError("Linux 服务器 ID 无效", "linuxServerId", value, exception);
        }
    }

    private OpencodeContainerId parseContainerId(String rawContainerId) {
        String value = textOrNull(rawContainerId);
        if (value == null) {
            return null;
        }
        try {
            return new OpencodeContainerId(value);
        } catch (IllegalArgumentException exception) {
            throw validationError("容器 ID 无效", "containerId", value, exception);
        }
    }

    private OpencodeContainerId parseRequiredContainerId(String rawContainerId) {
        OpencodeContainerId parsed = parseContainerId(rawContainerId);
        if (parsed == null) {
            throw validationError("容器 ID 无效", "containerId", rawContainerId, null);
        }
        return parsed;
    }

    private UserId parseUserId(String rawUserId) {
        String value = textOrNull(rawUserId);
        if (value == null) {
            return null;
        }
        try {
            return new UserId(value);
        } catch (IllegalArgumentException exception) {
            throw validationError("用户 ID 无效", "userId", value, exception);
        }
    }

    private BackendProcessId parseBackendProcessId(String rawBackendProcessId) {
        String value = textOrNull(rawBackendProcessId);
        if (value == null) {
            throw validationError("后端进程 ID 无效", "backendProcessId", rawBackendProcessId, null);
        }
        try {
            return new BackendProcessId(value);
        } catch (IllegalArgumentException exception) {
            throw validationError("后端进程 ID 无效", "backendProcessId", value, exception);
        }
    }

    private int parsePort(String rawPort) {
        String value = textOrNull(rawPort);
        if (value == null) {
            throw validationError("端口无效", "port", rawPort, null);
        }
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("port out of range");
            }
            return port;
        } catch (IllegalArgumentException exception) {
            throw validationError("端口无效", "port", value, exception);
        }
    }

    private Duration parseMetricWindow(Integer windowMinutes, Integer hours) {
        if (windowMinutes != null) {
            if (!ALLOWED_METRIC_WINDOW_MINUTES.contains(windowMinutes)) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "指标历史时间窗口无效",
                        Map.of("windowMinutes", windowMinutes));
            }
            return Duration.ofMinutes(windowMinutes);
        }
        if (hours == null) {
            return DEFAULT_METRIC_WINDOW;
        }
        return Duration.ofHours(parseMetricHours(hours));
    }

    private int parseMetricHours(Integer hours) {
        int value = hours;
        if (value < 1 || value > MAX_METRIC_HOURS) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "指标历史小时数无效",
                    Map.of("hours", value));
        }
        return value;
    }

    private int parseMetricMaxPoints(Integer maxPoints) {
        int value = maxPoints == null ? DEFAULT_METRIC_POINTS : maxPoints;
        if (value < 1 || value > MAX_METRIC_POINTS) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "指标历史点数无效",
                    Map.of("maxPoints", value));
        }
        return value;
    }

    private String parseUsername(String rawUsername) {
        return textOrNull(rawUsername);
    }

    private PlatformException validationError(String message, String fieldName, String value, IllegalArgumentException exception) {
        return new PlatformException(
                ErrorCode.VALIDATION_ERROR,
                message,
                Map.of(fieldName, value == null ? "" : value),
                exception);
    }

    private String textOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
