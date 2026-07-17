package com.enterprise.testagent.opencode.runtime.process.socket;

import java.lang.management.ManagementFactory;

/**
 * Windows 系统内存信息获取策略。
 * 使用 OperatingSystemMXBean 获取内存信息，该实现在 Windows 上通过 GlobalMemoryStatusEx API 工作。
 */
class WindowsMemoryProvider implements SystemMemoryProvider {

    @Override
    public MemoryInfo getMemoryInfo() {
        if (!(ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean osBean)) {
            return null;
        }
        Long totalBytes = positive(osBean.getTotalMemorySize());
        Long freeBytes = positive(osBean.getFreeMemorySize());
        if (totalBytes == null || freeBytes == null) {
            return null;
        }
        return new MemoryInfo(totalBytes, freeBytes);
    }

    private Long positive(long value) {
        return value > 0 ? value : null;
    }
}