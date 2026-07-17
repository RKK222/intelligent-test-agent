package com.enterprise.testagent.api.web.platform;

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
     * 统一认证登录请求体（通过 AAM 跳转后登录）。
     */
    public record UnifiedAuthLoginRequest(String unifiedAuthId, String token) {

        public UnifiedAuthLoginRequest {
            if (unifiedAuthId == null || unifiedAuthId.isBlank()) {
                throw new IllegalArgumentException("统一认证号不能为空");
            }
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("Token 不能为空");
            }
        }
    }

    /**
     * 登录成功响应体。
     */
    public record LoginResponse(String token, String userId, String username, String unifiedAuthId, java.util.List<String> roles) {
    }

    /**
     * 当前用户信息响应体。
     *
     * <p>roleLabels 是 {@code roles} 对应的中文展示名（来自 {@code dictionaries.dict_label}），
     * 供右上角用户菜单直接展示，不在前端再做 i18n 映射；多角色按当前用户实际顺序返回。
     */
    public record CurrentUserResponse(String userId, String username, String unifiedAuthId,
                                       String organization, String rdDepartment, String department,
                                       java.util.List<String> roles,
                                       java.util.List<String> roleLabels) {
    }
}
