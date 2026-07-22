package com.enterprise.testagent.opencode.runtime.process;

import java.util.Arrays;
import java.util.Optional;

/**
 * manager 拒绝启动时返回的稳定内部原因；平台迁移决策只依赖该枚举，不解析英文消息。
 */
public enum OpencodeProcessStartRejectionReason {
    PORT_CONFLICT(true),
    PORT_OUT_OF_RANGE(true),
    IDENTITY_ALREADY_MANAGED(false),
    IDENTITY_CONFIG_MISMATCH(false);

    private final boolean migratable;

    OpencodeProcessStartRejectionReason(boolean migratable) {
        this.migratable = migratable;
    }

    /** 只有端口冲突和范围变化允许在同一 Linux 服务器内迁移。 */
    public boolean migratable() {
        return migratable;
    }

    /** 按 manager 稳定 errorCode 精确解析，未知值继续走既有网关错误。 */
    public static Optional<OpencodeProcessStartRejectionReason> fromManagerErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(reason -> reason.name().equals(errorCode.trim()))
                .findFirst();
    }
}
