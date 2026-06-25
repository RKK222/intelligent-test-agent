package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.RuntimeManagementQueryService;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
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

    private final RuntimeManagementQueryService queryService;

    /**
     * 注入运行管理查询服务；Controller 不直接访问 Repository。
     */
    public RuntimeManagementController(RuntimeManagementQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
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

    private String parseUsername(String rawUsername) {
        return textOrNull(rawUsername);
    }

    private PlatformException validationError(String message, String fieldName, String value, IllegalArgumentException exception) {
        return new PlatformException(
                ErrorCode.VALIDATION_ERROR,
                message,
                Map.of(fieldName, value),
                exception);
    }

    private String textOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
