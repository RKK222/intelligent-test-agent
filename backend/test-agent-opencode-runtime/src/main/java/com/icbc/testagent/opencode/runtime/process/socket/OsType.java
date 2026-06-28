package com.icbc.testagent.opencode.runtime.process.socket;

import java.util.Locale;

/**
 * 操作系统类型枚举，用于选择正确的内存信息采集策略。
 */
enum OsType {
    MACOS,
    LINUX,
    WINDOWS,
    UNKNOWN;

    /**
     * 检测当前操作系统类型。
     */
    static OsType detect() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac") || osName.contains("darwin")) {
            return MACOS;
        }
        if (osName.contains("win")) {
            return WINDOWS;
        }
        if (osName.contains("nux") || osName.contains("nix") || osName.contains("aix")) {
            return LINUX;
        }
        return UNKNOWN;
    }
}