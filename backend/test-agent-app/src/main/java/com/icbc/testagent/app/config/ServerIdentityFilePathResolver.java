package com.icbc.testagent.app.config;

import com.icbc.testagent.domain.configuration.CommonParameterValues;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 从系统通用参数解析 Java/Go manager 共享的服务器身份与地址文件路径。
 */
final class ServerIdentityFilePathResolver {

    static final String SYS_DATA_ROOT_DIR = "SYS_DATA_ROOT_DIR";
    private static final String SERVER_ID_FILE_NAME = ".serverid";
    private static final String SERVER_HOST_FILE_NAME = ".serverhost";

    private final CommonParameterValues commonParameterValues;

    ServerIdentityFilePathResolver(CommonParameterValues commonParameterValues) {
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues, "commonParameterValues must not be null");
    }

    /**
     * 返回 {@code SYS_DATA_ROOT_DIR/.serverid}；参数缺失或空白时让启动流程失败。
     */
    Path serverIdFile() {
        return dataRoot().resolve(SERVER_ID_FILE_NAME);
    }

    /**
     * 返回 {@code SYS_DATA_ROOT_DIR/.serverhost}；manager 用它拼接 Java WebSocket 地址。
     */
    Path serverHostFile() {
        return dataRoot().resolve(SERVER_HOST_FILE_NAME);
    }

    private Path dataRoot() {
        String dataRoot = commonParameterValues.resolvedValue(SYS_DATA_ROOT_DIR)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("通用参数未配置：SYS_DATA_ROOT_DIR"));
        return Path.of(dataRoot.trim());
    }
}
