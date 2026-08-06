package com.enterprise.testagent.api.web.platform;

import java.util.List;

/**
 * 用户管理 API 的请求 DTO。
 */
public final class UserManagementDtos {

    private UserManagementDtos() {
    }

    /**
     * 创建测试用户请求体。密码由后端注入默认值 123456，前端不传明文。
     */
    public record CreateUserRequest(
            String unifiedAuthId,
            String username,
            String organization,
            String rdDepartment,
            String department,
            String role) {

        /**
         * 校验必填字段：统一认证号、用户名、角色不能为空。
         */
        public CreateUserRequest {
            if (unifiedAuthId == null || unifiedAuthId.isBlank()) {
                throw new IllegalArgumentException("统一认证号不能为空");
            }
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("用户名不能为空");
            }
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("角色不能为空");
            }
        }
    }

    /** 手工修正用户名请求体；统一认证号继续保持只读。 */
    public record UpdateUsernameRequest(String username) {

        /** 去除首尾空白并按 users.username 的字段长度校验。 */
        public UpdateUsernameRequest {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("用户名不能为空");
            }
            username = username.trim();
            if (username.length() > 128) {
                throw new IllegalArgumentException("用户名不能超过 128 个字符");
            }
        }
    }

    /**
     * 更新用户角色请求体。当前测试管理入口只接收单个全局角色 code。
     */
    public record UpdateUserRoleRequest(String role) {

        /**
         * 校验角色不能为空，具体角色合法性由业务层按 ROLE 字典校验。
         */
        public UpdateUserRoleRequest {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("角色不能为空");
            }
        }
    }

    /** 单个显式选中用户的目标角色。 */
    public record UserRoleAssignmentRequest(String userId, String role) {

        /** 拒绝不完整角色项，具体用户和角色合法性由应用服务统一校验。 */
        public UserRoleAssignmentRequest {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("用户 ID 不能为空");
            }
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("角色不能为空");
            }
        }
    }

    /** “选择全部检索结果”使用的稳定筛选快照，不包含分页字段。 */
    public record UserManagementFilterRequest(
            String keyword,
            String role,
            String organization,
            String rdDepartment,
            String department) {
    }

    /**
     * 批量角色修改请求。
     *
     * <p>显式选择时提交 assignments；选择全部检索结果时提交 allMatching=true、统一 role 和 filter。
     * 两种模式互斥，避免服务端误解批量作用范围。
     */
    public record UpdateUserRolesRequest(
            List<UserRoleAssignmentRequest> assignments,
            Boolean allMatching,
            String role,
            UserManagementFilterRequest filter) {

        /** 兼容旧前端缺省 allMatching，并校验两种选择模式互斥。 */
        public UpdateUserRolesRequest {
            boolean selectsAllMatching = Boolean.TRUE.equals(allMatching);
            boolean hasAssignments = assignments != null && !assignments.isEmpty();
            if (selectsAllMatching == hasAssignments) {
                throw new IllegalArgumentException("显式用户与全部检索结果必须且只能选择一种");
            }
            if (selectsAllMatching) {
                if (role == null || role.isBlank()) {
                    throw new IllegalArgumentException("角色不能为空");
                }
                if (filter == null) {
                    throw new IllegalArgumentException("全部检索结果筛选不能为空");
                }
            } else {
                assignments = List.copyOf(assignments);
            }
            allMatching = selectsAllMatching;
        }
    }

    /**
     * 批量用户操作请求。具体数量上限由业务服务按删除或 TCDS 同步场景分别校验。
     */
    public record UserIdsRequest(List<String> userIds) {

        /**
         * 拒绝空请求，并复制列表避免 Controller 调用后被外部修改。
         */
        public UserIdsRequest {
            if (userIds == null || userIds.isEmpty()) {
                throw new IllegalArgumentException("用户 ID 列表不能为空");
            }
            if (userIds.stream().anyMatch(userId -> userId == null || userId.isBlank())) {
                throw new IllegalArgumentException("用户 ID 不能为空");
            }
            userIds = List.copyOf(userIds);
        }
    }
}
