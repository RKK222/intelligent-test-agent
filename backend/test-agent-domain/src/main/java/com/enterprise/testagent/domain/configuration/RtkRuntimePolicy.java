package com.enterprise.testagent.domain.configuration;

import java.util.Locale;

/**
 * RTK 命令改写策略的唯一解析入口。
 *
 * <p>策略来自通用参数，缺失、空白或非法值都按关闭处理，保证新能力默认不会改变既有命令语义。</p>
 */
public final class RtkRuntimePolicy {

    public static final String PARAMETER_ENGLISH_NAME = "RTK_COMMAND_REWRITE_ENABLED";
    public static final String MANAGED_CONFIG_FIELD = "rtkEnabled";

    private RtkRuntimePolicy() {
    }

    /** 返回当前平台生效的 RTK 开关；只有明确的 true 才启用。 */
    public static boolean enabled(CommonParameterValues values) {
        if (values == null) {
            return false;
        }
        try {
            return values.resolvedValue(PARAMETER_ENGLISH_NAME)
                    .map(String::trim)
                    .map(value -> "true".equals(value.toLowerCase(Locale.ROOT)))
                    .orElse(false);
        } catch (RuntimeException ignored) {
            // 通用参数读取失败时按默认关闭处理，不能因为可选的 RTK 能力阻断 OpenCode 启动。
            return false;
        }
    }
}
