package com.enterprise.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OsTypeTest {

    @Test
    void detectReturnsCorrectTypeForMacOs() {
        // 在 macOS 环境下测试
        OsType detected = OsType.detect();
        // 验证检测逻辑能正确处理 os.name 属性
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac") || osName.contains("darwin")) {
            assertThat(detected).isEqualTo(OsType.MACOS);
        }
    }

    @Test
    void detectReturnsCorrectTypeForWindows() {
        // 在 Windows 环境下测试
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            assertThat(OsType.detect()).isEqualTo(OsType.WINDOWS);
        }
    }

    @Test
    void detectReturnsCorrectTypeForLinux() {
        // 在 Linux 环境下测试
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("nux") || osName.contains("nix")) {
            assertThat(OsType.detect()).isEqualTo(OsType.LINUX);
        }
    }
}