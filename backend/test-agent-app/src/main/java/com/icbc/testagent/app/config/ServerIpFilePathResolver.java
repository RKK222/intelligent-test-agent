package com.icbc.testagent.app.config;

import com.icbc.testagent.domain.configuration.CommonParameterValues;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 从系统通用参数解析 Java/Go manager 共享的服务器 IPv4 文件路径。
 */
final class ServerIpFilePathResolver {

    static final String SYS_DATA_ROOT_DIR = "SYS_DATA_ROOT_DIR";
    private static final String SERVER_IP_FILE_NAME = ".serverip";

    private final CommonParameterValues commonParameterValues;

    ServerIpFilePathResolver(CommonParameterValues commonParameterValues) {
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues, "commonParameterValues must not be null");
    }

    /**
     * 返回 {@code SYS_DATA_ROOT_DIR/.serverip}；参数缺失或空白时让启动流程失败，避免 manager 读取错误路径。
     */
    Path resolve() {
        String dataRoot = commonParameterValues.resolvedValue(SYS_DATA_ROOT_DIR)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("通用参数未配置：SYS_DATA_ROOT_DIR"));
        return Path.of(dataRoot.trim()).resolve(SERVER_IP_FILE_NAME);
    }
}
