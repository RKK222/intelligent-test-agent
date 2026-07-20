package com.enterprise.testagent.system.management.user;

import java.time.Instant;
import java.util.List;

/**
 * 用户管理对 API 层暴露的响应模型与命令。
 *
 * <p>该功能仅用于研发测试便捷造号，所有响应均不包含密码哈希等敏感字段。
 */
public final class UserManagementResponses {

    private UserManagementResponses() {
    }

    /**
     * 创建用户命令，由 Controller 将请求 DTO 转换而来，避免业务层依赖 API 模块。
     *
     * <p>role 为单个角色 code（如 SUPER_ADMIN），由业务层校验并授权。
     */
    public record CreateUserCommand(
            String unifiedAuthId,
            String username,
            String organization,
            String rdDepartment,
            String department,
            String role) {
    }

    /**
     * 更新用户全局角色命令。当前测试管理入口只允许设置单个全局角色，避免和登录态多角色展示混淆。
     */
    public record UpdateUserRoleCommand(String userId, String role) {
    }

    /**
     * 用户列表/详情响应，roles 为角色 code 列表，roleLabels 为对应中文展示名。
     */
    public record UserResponse(
            String userId,
            String username,
            String unifiedAuthId,
            String organization,
            String rdDepartment,
            String department,
            String status,
            List<String> roles,
            List<String> roleLabels,
            Instant createdAt) {
    }

    /**
     * 可选角色下拉项，roleCode 对应字典 dict_value，roleLabel 对应字典 dict_label。
     */
    public record RoleOption(String roleCode, String roleLabel) {
    }

    /**
     * 第三方用户信息接口响应模型。
     */
    public record ThirdPartyUserInfoResponse(
            String fullname,
            String loginname,
            String basement,
            String departname) {
    }
}
