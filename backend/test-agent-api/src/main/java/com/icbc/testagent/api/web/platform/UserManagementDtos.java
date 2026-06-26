package com.icbc.testagent.api.web.platform;

/**
 * 用户管理（测试）API 的请求 DTO。
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
}
