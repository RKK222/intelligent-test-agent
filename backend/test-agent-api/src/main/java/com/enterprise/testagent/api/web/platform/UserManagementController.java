package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.system.management.user.UserManagementApplicationService;
import com.enterprise.testagent.system.management.user.UserManagementResponses.CreateUserCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.DeleteUsersCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.SyncUsersFromTcdsCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.UpdateUserRoleCommand;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 用户管理 HTTP 入口，仅做鉴权、参数转换和统一响应包装。
 *
 * <p>所有接口仅限 SUPER_ADMIN 访问，用于研发测试查询用户、创建测试用户（默认密码 123456）。
 */
@RestController
@RequestMapping("/api/internal/platform/system-management")
public class UserManagementController {

    private final UserManagementApplicationService service;

    /**
     * 注入用户管理应用服务。
     */
    public UserManagementController(UserManagementApplicationService service) {
        this.service = service;
    }

    /**
     * 分页查询用户列表，可按关键字匹配用户名/统一认证号。
     */
    @GetMapping("/users")
    public ApiResponse<Object> listUsers(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.listUsers(keyword, RuntimeApiSupport.pageRequest(page, size)));
    }

    /**
     * 创建测试用户，密码由后端注入默认值，并授予请求中指定的单个角色。
     */
    @PostMapping("/users")
    public ApiResponse<Object> createUser(
            @RequestBody UserManagementDtos.CreateUserRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        CreateUserCommand command = new CreateUserCommand(
                request.unifiedAuthId(),
                request.username(),
                request.organization(),
                request.rdDepartment(),
                request.department(),
                request.role());
        return ok(exchange, service.createUser(command));
    }

    /**
     * 调整指定用户的全局角色，当前测试入口只支持设置单个角色。
     */
    @PutMapping("/users/{userId}/roles")
    public ApiResponse<Object> updateUserRole(
            @PathVariable("userId") String userId,
            @RequestBody UserManagementDtos.UpdateUserRoleRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.updateUserRole(new UpdateUserRoleCommand(userId, request.role())));
    }

    /**
     * 删除单个未承载业务资产的存量用户，同时禁止删除当前登录账号。
     */
    @DeleteMapping("/users/{userId}")
    public ApiResponse<Object> deleteUser(
            @PathVariable("userId") String userId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireSuperAdmin(exchange);
        return ok(exchange, service.deleteUsers(new DeleteUsersCommand(
                principal.userId().value(),
                List.of(userId))));
    }

    /**
     * 批量删除未承载业务资产的存量用户；任一目标不满足条件时整批不删除。
     */
    @PostMapping("/users/batch-delete")
    public ApiResponse<Object> deleteUsers(
            @RequestBody UserManagementDtos.UserIdsRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireSuperAdmin(exchange);
        return ok(exchange, service.deleteUsers(new DeleteUsersCommand(
                principal.userId().value(),
                request.userIds())));
    }

    /**
     * 从 TCDS 原位同步单个用户的姓名和部门，既有应用、会话和工作区关联保持不变。
     */
    @PostMapping("/users/{userId}/tcds-sync")
    public ApiResponse<Object> syncUserFromTcds(
            @PathVariable("userId") String userId,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.syncUsersFromTcds(new SyncUsersFromTcdsCommand(List.of(userId))));
    }

    /**
     * 批量从 TCDS 原位同步姓名和部门；全部查询成功后才统一写库。
     */
    @PostMapping("/users/tcds-sync")
    public ApiResponse<Object> syncUsersFromTcds(
            @RequestBody UserManagementDtos.UserIdsRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.syncUsersFromTcds(new SyncUsersFromTcdsCommand(request.userIds())));
    }

    /**
     * 查询可选角色列表，供前端新增用户下拉选择。
     */
    @GetMapping("/roles")
    public ApiResponse<Object> listRoles(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.listRoles());
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
