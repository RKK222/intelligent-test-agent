package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.system.management.maintenance.DatabaseIdentityMaintenanceService;
import com.enterprise.testagent.system.management.maintenance.DatabaseIdentityResponses.RestartIdentityCommand;
import com.enterprise.testagent.domain.maintenance.IdentityManagedTable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 数据库 identity 运维 HTTP 入口，仅做鉴权、参数转换和统一响应包装。
 *
 * <p>所有接口仅限 SUPER_ADMIN 访问，用于查询白名单表 identity 状态并手工滚动序列，
 * 修复 identity 落后于已有主键导致新增数据冲突的问题。
 */
@RestController
@RequestMapping("/api/internal/platform/system-management/identity")
public class DatabaseIdentityController {

    private final DatabaseIdentityMaintenanceService service;

    /**
     * 注入 identity 运维应用服务。
     */
    public DatabaseIdentityController(DatabaseIdentityMaintenanceService service) {
        this.service = service;
    }

    /**
     * 查询白名单内全部表的 identity 当前值、max(id) 与是否错位。
     */
    @GetMapping
    public ApiResponse<Object> listIdentityStatuses(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.listIdentityStatuses());
    }

    /**
     * 把指定表 identity 对齐到 max(id)+1。
     */
    @PostMapping("/align")
    public ApiResponse<Object> alignIdentity(
            @RequestBody DatabaseIdentityDtos.AlignIdentityRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        IdentityManagedTable table = DatabaseIdentityMaintenanceService.requireTable(request.table());
        return ok(exchange, service.alignIdentity(table));
    }

    /**
     * 手动把指定表 identity 重启到目标值，目标值必须大于当前 max(id)。
     */
    @PostMapping("/restart")
    public ApiResponse<Object> restartIdentity(
            @RequestBody DatabaseIdentityDtos.RestartIdentityRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        IdentityManagedTable table = DatabaseIdentityMaintenanceService.requireTable(request.table());
        return ok(exchange, service.restartIdentity(
                new RestartIdentityCommand(table, request.targetValue())));
    }

    /**
     * 校验当前主体为超级管理员，否则抛 FORBIDDEN。
     */
    private AuthPrincipal requireSuperAdmin(ServerWebExchange exchange) {
        return AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    private ApiResponse<Object> ok(ServerWebExchange exchange, Object data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }
}
