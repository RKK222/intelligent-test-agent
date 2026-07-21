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
     * 删除用户命令。operatorUserId 用于强制禁止超级管理员删除当前登录账号。
     */
    public record DeleteUsersCommand(String operatorUserId, List<String> userIds) {
    }

    /**
     * 从 TCDS 原位同步用户姓名和部门的命令；保留 userId 及其全部既有关联。
     */
    public record SyncUsersFromTcdsCommand(List<String> userIds) {
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
     * 单个或批量删除结果；批量操作保持事务原子性，成功时列表即全部目标用户。
     */
    public record DeleteUsersResponse(List<String> deletedUserIds, int deletedCount) {
    }

    /**
     * 单个或批量 TCDS 同步结果；应用、会话和工作区关联因 userId 不变而原样保留。
     */
    public record SyncUsersFromTcdsResponse(List<String> syncedUserIds, int syncedCount) {
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
