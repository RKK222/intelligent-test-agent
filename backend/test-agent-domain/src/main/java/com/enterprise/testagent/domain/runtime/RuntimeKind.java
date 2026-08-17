package com.enterprise.testagent.domain.runtime;

/** OpenCode 运行目标类型；缺失字段的历史数据按 SERVER_PROCESS 解释。 */
public enum RuntimeKind {
    SERVER_PROCESS,
    LOCAL_CLIENT;

    public static RuntimeKind fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return SERVER_PROCESS;
        }
        return RuntimeKind.valueOf(value.trim().toUpperCase());
    }

    public static RuntimeKind fromNullable(RuntimeKind value) {
        return value == null ? SERVER_PROCESS : value;
    }
}
