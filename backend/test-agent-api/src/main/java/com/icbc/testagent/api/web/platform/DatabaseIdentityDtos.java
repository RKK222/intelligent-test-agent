package com.icbc.testagent.api.web.platform;

/**
 * identity 运维 HTTP 请求 DTO，与 UserManagementDtos 同风格。
 */
public final class DatabaseIdentityDtos {

    private DatabaseIdentityDtos() {
    }

    /**
     * 对齐请求：只需表码（白名单枚举名）。
     */
    public record AlignIdentityRequest(String table) {

        public AlignIdentityRequest {
            if (table == null || table.isBlank()) {
                throw new IllegalArgumentException("表不能为空");
            }
        }
    }

    /**
     * 手动重启请求：表码 + 目标值，目标值必须为正整数且大于当前 max(id)（由服务层校验）。
     */
    public record RestartIdentityRequest(String table, Long targetValue) {

        public RestartIdentityRequest {
            if (table == null || table.isBlank()) {
                throw new IllegalArgumentException("表不能为空");
            }
            if (targetValue == null) {
                throw new IllegalArgumentException("目标值不能为空");
            }
        }
    }
}
