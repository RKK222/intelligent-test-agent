package com.example.testagent.api.web.platform;

/**
 * 认证相关 API 的请求和响应 DTO。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * 登录请求体。
     */
    public record LoginRequest(String username, String password) {

        /**
         * 校验登录请求字段不能为空。
         */
        public LoginRequest {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("用户名不能为空");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("密码不能为空");
            }
        }
    }

    /**
     * 登录成功响应体。
     */
    public record LoginResponse(String token, String userId, String username, String unifiedAuthId) {
    }

    /**
     * 当前用户信息响应体。
     */
    public record CurrentUserResponse(String userId, String username, String unifiedAuthId,
                                       String organization, String rdDepartment, String department) {
    }
}
