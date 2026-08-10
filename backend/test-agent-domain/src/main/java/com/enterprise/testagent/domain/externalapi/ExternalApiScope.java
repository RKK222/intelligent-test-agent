package com.enterprise.testagent.domain.externalapi;

/** 外部工具凭据可被授予的最小权限集合。 */
public enum ExternalApiScope {
    USER_SSH_KEY_READ("读取用户 SSH 私钥");

    private final String description;

    ExternalApiScope(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** 以稳定枚举名解析数据库或 HTTP 输入，未知值直接拒绝。 */
    public static ExternalApiScope fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        return ExternalApiScope.valueOf(value.trim());
    }
}
