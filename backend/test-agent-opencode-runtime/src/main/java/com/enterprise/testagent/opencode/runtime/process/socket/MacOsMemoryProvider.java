package com.enterprise.testagent.opencode.runtime.process.socket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * macOS 系统内存信息获取策略。
 * macOS 的内存管理与 Linux/Windows 不同，OperatingSystemMXBean.getFreeMemorySize() 返回值不准确。
 * 该实现通过执行 vm_stat 命令获取详细内存统计信息。
 */
class MacOsMemoryProvider implements SystemMemoryProvider {

    private static final Logger log = LoggerFactory.getLogger(MacOsMemoryProvider.class);
    private static final Pattern VM_STAT_LINE = Pattern.compile("^([^:]+):\\s+(\\d+)\\.$");

    @Override
    public MemoryInfo getMemoryInfo() {
        // 总物理内存可以通过 OperatingSystemMXBean 获取
        Long totalBytes = getTotalMemoryFromOsBean();
        if (totalBytes == null) {
            return null;
        }

        // 可用内存需要通过 vm_stat 命令计算
        Long availableBytes = getAvailableMemoryFromVmStat();
        if (availableBytes == null) {
            // vm_stat 失败时降级到 OperatingSystemMXBean（虽然不准确，但总比没有好）
            availableBytes = getFreeMemoryFromOsBean();
        }

        if (availableBytes == null) {
            return null;
        }

        return new MemoryInfo(totalBytes, availableBytes);
    }

    private Long getTotalMemoryFromOsBean() {
        if (!(ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean osBean)) {
            return null;
        }
        return positive(osBean.getTotalMemorySize());
    }

    private Long getFreeMemoryFromOsBean() {
        if (!(ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean osBean)) {
            return null;
        }
        return positive(osBean.getFreeMemorySize());
    }

    /**
     * 执行 vm_stat 命令获取内存统计信息。
     * macOS 可用内存 = free + inactive + speculative
     * 这些内存都可以被系统回收供应用程序使用。
     */
    private Long getAvailableMemoryFromVmStat() {
        try {
            ProcessBuilder pb = new ProcessBuilder("vm_stat");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            Map<String, Long> stats = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = VM_STAT_LINE.matcher(line.trim());
                    if (matcher.matches()) {
                        String key = matcher.group(1).trim().toLowerCase(Locale.ROOT);
                        long value = Long.parseLong(matcher.group(2));
                        stats.put(key, value);
                    }
                }
            }

            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }

            if (process.exitValue() != 0) {
                return null;
            }

            // vm_stat 输出的值单位是页面，需要乘以页面大小（通常 4096 字节）
            long pageSize = getPageSize();

            // 获取关键指标
            Long free = stats.get("pages free");
            Long inactive = stats.get("pages inactive");
            Long speculative = stats.get("pages speculative");

            if (free == null) {
                return null;
            }

            // 可用内存 = free + inactive + speculative
            long availablePages = free;
            if (inactive != null) {
                availablePages += inactive;
            }
            if (speculative != null) {
                availablePages += speculative;
            }

            return availablePages * pageSize;
        } catch (IOException | InterruptedException e) {
            log.debug("Failed to get memory info from vm_stat: {}", e.getMessage());
            return null;
        } catch (NumberFormatException e) {
            log.debug("Failed to parse vm_stat output: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取系统页面大小。
     */
    private long getPageSize() {
        // macOS 默认页面大小为 4096 字节
        // 可以通过 sysctl hw.pagesize 获取，但通常不需要
        try {
            ProcessBuilder pb = new ProcessBuilder("sysctl", "-n", "hw.pagesize");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    return Long.parseLong(line.trim());
                }
            }
        } catch (Exception e) {
            // 忽略错误，使用默认值
        }
        return 4096L;
    }

    private Long positive(long value) {
        return value > 0 ? value : null;
    }
}