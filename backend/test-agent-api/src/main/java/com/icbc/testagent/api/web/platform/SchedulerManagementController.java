package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunFilter;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunId;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.icbc.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.scheduler.ScheduledTaskUpdateCommand;
import com.icbc.testagent.scheduler.SchedulerManagementService;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 定时任务管理 API，仅限 SUPER_ADMIN 访问；Controller 不直接访问 Repository 或 Redis。
 */
@RestController
@RequestMapping("/api/internal/platform/scheduler-management")
public class SchedulerManagementController {

    private final SchedulerManagementService service;

    /**
     * 注入 scheduler 管理服务。
     */
    public SchedulerManagementController(SchedulerManagementService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @GetMapping("/tasks")
    public Mono<ApiResponse<Object>> tasks(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        PageRequest pageRequest = RuntimeApiSupport.pageRequest(page, size);
        return blocking(traceId, () -> SchedulerManagementDtos.taskPage(service.findTasks(pageRequest), service));
    }

    @GetMapping("/tasks/{taskKey}")
    public Mono<ApiResponse<Object>> task(@PathVariable String taskKey, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        ScheduledTaskKey key = parseTaskKey(taskKey);
        return blocking(traceId, () -> SchedulerManagementDtos.TaskResponse.from(service.getTask(key), service));
    }

    @PatchMapping("/tasks/{taskKey}")
    public Mono<ApiResponse<Object>> updateTask(
            @PathVariable String taskKey,
            @RequestBody SchedulerManagementDtos.UpdateTaskRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        ScheduledTaskKey key = parseTaskKey(taskKey);
        ScheduledTaskUpdateCommand command = new ScheduledTaskUpdateCommand(
                request == null ? null : request.enabled(),
                request == null ? null : request.cronExpression(),
                request == null || request.lockTtlSeconds() == null ? null : Duration.ofSeconds(request.lockTtlSeconds()));
        return blocking(traceId, () -> SchedulerManagementDtos.TaskResponse.from(service.updateTask(key, command, traceId), service));
    }

    @PostMapping("/tasks/{taskKey}/trigger")
    public Mono<ApiResponse<Object>> trigger(@PathVariable String taskKey, ServerWebExchange exchange) {
        AuthPrincipal principal = requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        ScheduledTaskKey key = parseTaskKey(taskKey);
        return blocking(traceId, () -> SchedulerManagementDtos.RunResponse.from(service.trigger(key, principal.userId(), traceId), service));
    }

    @GetMapping("/runs")
    public Mono<ApiResponse<Object>> runs(
            @RequestParam(required = false) String taskKey,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) String requestedByUserId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        PageRequest pageRequest = RuntimeApiSupport.pageRequest(page, size);
        ScheduledTaskRunFilter filter = new ScheduledTaskRunFilter(
                parseOptionalTaskKey(taskKey),
                parseStatus(status),
                parseTriggerType(triggerType),
                parseUserId(requestedByUserId));
        return blocking(traceId, () -> SchedulerManagementDtos.runPage(service.findRuns(filter, pageRequest), service));
    }

    @GetMapping("/runs/{taskRunId}")
    public Mono<ApiResponse<Object>> run(@PathVariable String taskRunId, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        ScheduledTaskRunId runId = parseTaskRunId(taskRunId);
        return blocking(traceId, () -> SchedulerManagementDtos.RunResponse.from(service.getRun(runId), service));
    }

    @PostMapping("/runs/{taskRunId}/stop")
    public Mono<ApiResponse<Object>> stopRun(@PathVariable String taskRunId, ServerWebExchange exchange) {
        AuthPrincipal principal = requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        ScheduledTaskRunId runId = parseTaskRunId(taskRunId);
        return blocking(traceId, () -> SchedulerManagementDtos.RunResponse.from(
                service.stopRun(runId, principal.userId(), traceId),
                service));
    }

    private AuthPrincipal requireSuperAdmin(ServerWebExchange exchange) {
        return AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    private Mono<ApiResponse<Object>> blocking(String traceId, java.util.concurrent.Callable<Object> supplier) {
        return Mono.fromCallable(() -> ApiResponse.ok(supplier.call(), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ScheduledTaskKey parseTaskKey(String rawTaskKey) {
        try {
            return new ScheduledTaskKey(rawTaskKey);
        } catch (IllegalArgumentException exception) {
            throw validationError("定时任务 key 无效", "taskKey", rawTaskKey, exception);
        }
    }

    private ScheduledTaskKey parseOptionalTaskKey(String rawTaskKey) {
        String value = textOrNull(rawTaskKey);
        return value == null ? null : parseTaskKey(value);
    }

    private ScheduledTaskRunId parseTaskRunId(String rawTaskRunId) {
        try {
            return new ScheduledTaskRunId(rawTaskRunId);
        } catch (IllegalArgumentException exception) {
            throw validationError("定时任务运行记录 ID 无效", "taskRunId", rawTaskRunId, exception);
        }
    }

    private ScheduledTaskRunStatus parseStatus(String rawStatus) {
        String value = textOrNull(rawStatus);
        if (value == null) {
            return null;
        }
        try {
            return ScheduledTaskRunStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validationError("定时任务运行状态无效", "status", value, exception);
        }
    }

    private ScheduledTaskTriggerType parseTriggerType(String rawTriggerType) {
        String value = textOrNull(rawTriggerType);
        if (value == null) {
            return null;
        }
        try {
            return ScheduledTaskTriggerType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validationError("定时任务触发类型无效", "triggerType", value, exception);
        }
    }

    private UserId parseUserId(String rawUserId) {
        String value = textOrNull(rawUserId);
        if (value == null) {
            return null;
        }
        try {
            return new UserId(value);
        } catch (IllegalArgumentException exception) {
            throw validationError("用户 ID 无效", "requestedByUserId", value, exception);
        }
    }

    private PlatformException validationError(String message, String fieldName, String value, IllegalArgumentException exception) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message, Map.of(fieldName, value), exception);
    }

    private String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
