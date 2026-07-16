package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.configuration.management.CommonParameterManagementApplicationService;
import com.enterprise.testagent.configuration.management.CommonParameterManagementApplicationService.CommonParameterFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 通用参数管理 API，仅限 SUPER_ADMIN 访问；提供列表查询与「仅修改 value」更新，
 * 不暴露新增/删除接口。Controller 不直接访问 Repository。
 */
@RestController
@RequestMapping("/api/internal/platform/configuration-management/common-parameters")
public class CommonParameterManagementController {

    private final CommonParameterManagementApplicationService service;

    /**
     * 注入通用参数管理应用服务。
     */
    public CommonParameterManagementController(CommonParameterManagementApplicationService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    /**
     * 列出通用参数，支持按平台过滤与分页。
     */
    @GetMapping
    public Mono<ApiResponse<Object>> list(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        PageRequest pageRequest = RuntimeApiSupport.pageRequest(page, size);
        CommonParameterFilter filter = CommonParameterFilter.parse(platform);
        return blocking(traceId, () -> service.find(filter, pageRequest));
    }

    /**
     * 仅更新指定通用参数的 value。
     */
    @PatchMapping("/{parameterId}")
    public Mono<ApiResponse<Object>> updateValue(
            @PathVariable String parameterId,
            @RequestBody(required = false) CommonParameterManagementDtos.UpdateValueRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        String newValue = request == null ? null : request.value();
        String userId = principal.userId() != null ? principal.userId().value() : null;
        String username = principal.username();
        return blocking(traceId, () -> service.updateValue(parameterId, newValue, traceId, userId, username));
    }

    /**
     * 查询指定通用参数的修改历史记录。
     */
    @GetMapping("/{parameterId}/change-logs")
    public Mono<ApiResponse<Object>> getChangeLogs(
            @PathVariable String parameterId,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return blocking(traceId, () -> service.findChangeLogs(parameterId));
    }

    private AuthPrincipal requireSuperAdmin(ServerWebExchange exchange) {
        return AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    private Mono<ApiResponse<Object>> blocking(String traceId, java.util.concurrent.Callable<Object> supplier) {
        return Mono.fromCallable(() -> ApiResponse.ok(supplier.call(), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
