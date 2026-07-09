package com.icbc.testagent.opencode.runtime.process.socket;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Linux Java 进程 OS 视角指标采集器，补充 JVM MXBean 看不到的 RSS、虚拟内存、swap 和 FD 指标。
 */
final class LinuxJavaProcessMetricsProvider {

    private static final long KIB = 1024L;

    private final Path statusPath;
    private final Path fdPath;
    private final boolean preferUnixMxBean;

    LinuxJavaProcessMetricsProvider() {
        this(Path.of("/proc/self/status"), Path.of("/proc/self/fd"), true);
    }

    LinuxJavaProcessMetricsProvider(Path statusPath, Path fdPath, boolean preferUnixMxBean) {
        this.statusPath = statusPath == null ? Path.of("/proc/self/status") : statusPath;
        this.fdPath = fdPath == null ? Path.of("/proc/self/fd") : fdPath;
        this.preferUnixMxBean = preferUnixMxBean;
    }

    JavaProcessOsMetrics sample() {
        Map<String, Long> status = readStatus();
        return new JavaProcessOsMetrics(
                positive(status.get("VmRSS")),
                positive(status.get("VmHWM")),
                positive(status.get("VmSize")),
                positive(status.get("VmSwap")),
                positive(status.get("Threads")),
                openFileDescriptorCount(),
                maxFileDescriptorCount());
    }

    private Map<String, Long> readStatus() {
        Map<String, Long> result = new HashMap<>();
        try {
            for (String line : Files.readAllLines(statusPath, StandardCharsets.US_ASCII)) {
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon);
                String valuePart = line.substring(colon + 1).trim();
                if (valuePart.isEmpty()) {
                    continue;
                }
                String[] parts = valuePart.split("\\s+");
                try {
                    long value = Long.parseLong(parts[0]);
                    long multiplier = parts.length > 1 && "kB".equalsIgnoreCase(parts[1]) ? KIB : 1L;
                    result.put(key, value * multiplier);
                } catch (NumberFormatException ignored) {
                    // /proc/self/status 包含 Name、State 等非数值字段，跳过即可。
                }
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return result;
    }

    private Long openFileDescriptorCount() {
        if (preferUnixMxBean
                && ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.UnixOperatingSystemMXBean unixBean) {
            long value = unixBean.getOpenFileDescriptorCount();
            if (value >= 0) {
                return value;
            }
        }
        try (Stream<Path> paths = Files.list(fdPath)) {
            return paths.count();
        } catch (IOException ignored) {
            return null;
        }
    }

    private Long maxFileDescriptorCount() {
        if (preferUnixMxBean
                && ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.UnixOperatingSystemMXBean unixBean) {
            long value = unixBean.getMaxFileDescriptorCount();
            return value >= 0 ? value : null;
        }
        return null;
    }

    private static Long positive(Long value) {
        return value != null && value > 0 ? value : null;
    }

    record JavaProcessOsMetrics(
            Long residentMemoryBytes,
            Long peakResidentMemoryBytes,
            Long virtualMemoryBytes,
            Long swapBytes,
            Long processThreads,
            Long openFileDescriptorCount,
            Long maxFileDescriptorCount) {
    }
}
