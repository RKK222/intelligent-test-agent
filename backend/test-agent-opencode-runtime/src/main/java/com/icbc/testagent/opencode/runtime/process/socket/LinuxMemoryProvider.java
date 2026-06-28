package com.icbc.testagent.opencode.runtime.process.socket;

import java.lang.management.ManagementFactory;

/**
 * Linux 系统内存信息获取策略。
 * 使用 OperatingSystemMXBean 获取内存信息，该实现在 Linux 上通过读取 /proc/meminfo 工作。
 */
class LinuxMemoryProvider implements SystemMemoryProvider {

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
        // Linux 下可用内存 = 空闲内存 + 缓冲区 + 缓存（可回收部分）
        // OperatingSystemMXBean.getFreeMemorySize() 返回的是 MemAvailable 或 MemFree
        return new MemoryInfo(totalBytes, freeBytes);
    }

    private Long positive(long value) {
        return value > 0 ? value : null;
    }
}