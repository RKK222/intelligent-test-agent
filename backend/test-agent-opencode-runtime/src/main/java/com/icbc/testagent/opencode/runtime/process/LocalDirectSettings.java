package com.icbc.testagent.opencode.runtime.process;

/**
 * 本地开发短路模式运行参数。
 *
 * <p>当 {@link #enabled()} 为 true 时，{@link UserOpencodeProcessAssignmentService} 不再校验
 * database 拓扑 / user binding / manager 健康检测，直接合成一个指向 {@link #baseUrl()} 的
 * READY 进程对象给前端。该模式仅供本地开发者使用，生产必须保持 false。
 */
public record LocalDirectSettings(boolean enabled, String baseUrl) {

    /**
     * 规整短路开关与 baseUrl；空值/空白 baseUrl 回退到默认 127.0.0.1:4096。
     */
    public LocalDirectSettings {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://127.0.0.1:4096";
        } else {
            baseUrl = baseUrl.trim();
        }
    }

    /**
     * 关闭短路的默认 settings。
     */
    public static LocalDirectSettings disabled() {
        return new LocalDirectSettings(false, "http://127.0.0.1:4096");
    }
}
