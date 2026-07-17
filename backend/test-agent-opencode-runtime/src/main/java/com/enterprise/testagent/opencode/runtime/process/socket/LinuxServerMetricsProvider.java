package com.enterprise.testagent.opencode.runtime.process.socket;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Linux 服务器级指标采集器，使用 /proc 口径对齐 top/free/sar 等系统工具。
 */
final class LinuxServerMetricsProvider {

    private static final long KIB = 1024L;

    private final Path meminfoPath;
    private final Path statPath;
    private final Path loadavgPath;
    private CpuTimes previousCpuTimes;

    LinuxServerMetricsProvider() {
        this(Path.of("/proc"));
    }

    LinuxServerMetricsProvider(Path procRoot) {
        Path root = procRoot == null ? Path.of("/proc") : procRoot;
        this.meminfoPath = root.resolve("meminfo");
        this.statPath = root.resolve("stat");
        this.loadavgPath = root.resolve("loadavg");
    }

    synchronized LinuxServerMetrics sample(Path diskPath) {
        Map<String, Long> meminfo = readMeminfo();
        CpuUsage cpuUsage = readCpuUsage();
        LoadAverage loadAverage = readLoadAverage();
        DiskUsage diskUsage = diskUsage(diskPath);

        Long memoryTotalBytes = positive(meminfo.get("MemTotal"));
        Long memoryAvailableBytes = positive(meminfo.get("MemAvailable"));
        if (memoryAvailableBytes == null) {
            memoryAvailableBytes = fallbackAvailableBytes(meminfo);
        }
        Long memoryFreeBytes = positive(meminfo.get("MemFree"));
        Long memoryBuffersBytes = positive(meminfo.get("Buffers"));
        Long memoryCachedBytes = positive(meminfo.get("Cached"));
        Long memoryUsedBytes = used(memoryTotalBytes, memoryAvailableBytes);

        Long swapTotalBytes = positive(meminfo.get("SwapTotal"));
        Long swapFreeBytes = positive(meminfo.get("SwapFree"));
        Long swapUsedBytes = used(swapTotalBytes, swapFreeBytes);

        return new LinuxServerMetrics(
                cpuUsage.usagePercent(),
                Runtime.getRuntime().availableProcessors(),
                loadAverage.oneMinute(),
                loadAverage.fiveMinutes(),
                loadAverage.fifteenMinutes(),
                memoryTotalBytes,
                memoryAvailableBytes,
                memoryFreeBytes,
                memoryUsedBytes,
                percent(memoryUsedBytes, memoryTotalBytes),
                memoryBuffersBytes,
                memoryCachedBytes,
                swapTotalBytes,
                swapFreeBytes,
                swapUsedBytes,
                percent(swapUsedBytes, swapTotalBytes),
                diskUsage.totalBytes(),
                diskUsage.availableBytes(),
                diskUsage.usedBytes(),
                percent(diskUsage.usedBytes(), diskUsage.totalBytes()));
    }

    private Map<String, Long> readMeminfo() {
        Map<String, Long> result = new HashMap<>();
        try {
            for (String line : Files.readAllLines(meminfoPath, StandardCharsets.US_ASCII)) {
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon);
                String[] parts = line.substring(colon + 1).trim().split("\\s+");
                if (parts.length == 0 || parts[0].isBlank()) {
                    continue;
                }
                long value = Long.parseLong(parts[0]);
                long multiplier = parts.length > 1 && "kB".equalsIgnoreCase(parts[1]) ? KIB : 1L;
                result.put(key, value * multiplier);
            }
        } catch (IOException | NumberFormatException ignored) {
            return Map.of();
        }
        return result;
    }

    private CpuUsage readCpuUsage() {
        try (Stream<String> lines = Files.lines(statPath, StandardCharsets.US_ASCII)) {
            String cpuLine = lines.filter(line -> line.startsWith("cpu ")).findFirst().orElse(null);
            if (cpuLine == null) {
                return new CpuUsage(null);
            }
            CpuTimes current = CpuTimes.parse(cpuLine);
            CpuTimes previous = previousCpuTimes;
            previousCpuTimes = current;
            if (previous == null) {
                return new CpuUsage(null);
            }
            long totalDelta = current.total() - previous.total();
            long idleDelta = current.idleAll() - previous.idleAll();
            if (totalDelta <= 0 || idleDelta < 0) {
                return new CpuUsage(null);
            }
            return new CpuUsage(clampPercent((1.0 - idleDelta / (double) totalDelta) * 100.0));
        } catch (IOException | NumberFormatException ignored) {
            return new CpuUsage(null);
        }
    }

    private LoadAverage readLoadAverage() {
        try {
            String[] parts = Files.readString(loadavgPath, StandardCharsets.US_ASCII).trim().split("\\s+");
            if (parts.length < 3) {
                return new LoadAverage(null, null, null);
            }
            return new LoadAverage(parseDouble(parts[0]), parseDouble(parts[1]), parseDouble(parts[2]));
        } catch (IOException ignored) {
            return new LoadAverage(null, null, null);
        }
    }

    private DiskUsage diskUsage(Path diskPath) {
        Path path = diskPath == null ? Path.of("").toAbsolutePath().normalize() : diskPath.toAbsolutePath().normalize();
        File file = path.toFile();
        Long totalBytes = positive(file.getTotalSpace());
        Long availableBytes = nonNegative(file.getUsableSpace());
        Long usedBytes = used(totalBytes, availableBytes);
        return new DiskUsage(totalBytes, availableBytes, usedBytes);
    }

    private Long fallbackAvailableBytes(Map<String, Long> meminfo) {
        Long memFree = positive(meminfo.get("MemFree"));
        if (memFree == null) {
            return null;
        }
        long available = memFree;
        available += positiveOrZero(meminfo.get("Buffers"));
        available += positiveOrZero(meminfo.get("Cached"));
        available += positiveOrZero(meminfo.get("SReclaimable"));
        available -= positiveOrZero(meminfo.get("Shmem"));
        return available > 0 ? available : memFree;
    }

    private static Long used(Long totalBytes, Long availableBytes) {
        if (totalBytes == null || availableBytes == null) {
            return null;
        }
        return Math.max(0L, totalBytes - Math.min(availableBytes, totalBytes));
    }

    private static Long positive(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private static Long positive(long value) {
        return value > 0 ? value : null;
    }

    private static Long nonNegative(long value) {
        return value >= 0 ? value : null;
    }

    private static long positiveOrZero(Long value) {
        return value != null && value > 0 ? value : 0L;
    }

    private static Double percent(Long used, Long total) {
        if (used == null || total == null || total <= 0) {
            return null;
        }
        return used.doubleValue() / total.doubleValue() * 100.0;
    }

    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double clampPercent(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private record CpuUsage(Double usagePercent) {
    }

    private record LoadAverage(Double oneMinute, Double fiveMinutes, Double fifteenMinutes) {
    }

    private record DiskUsage(Long totalBytes, Long availableBytes, Long usedBytes) {
    }

    record LinuxServerMetrics(
            Double cpuUsagePercent,
            Integer cpuCoreCount,
            Double loadAverage1m,
            Double loadAverage5m,
            Double loadAverage15m,
            Long memoryTotalBytes,
            Long memoryAvailableBytes,
            Long memoryFreeBytes,
            Long memoryUsedBytes,
            Double memoryUsagePercent,
            Long memoryBuffersBytes,
            Long memoryCachedBytes,
            Long swapTotalBytes,
            Long swapFreeBytes,
            Long swapUsedBytes,
            Double swapUsagePercent,
            Long diskTotalBytes,
            Long diskAvailableBytes,
            Long diskUsedBytes,
            Double diskUsagePercent) {
    }

    private record CpuTimes(
            long user,
            long nice,
            long system,
            long idle,
            long iowait,
            long irq,
            long softirq,
            long steal,
            long guest,
            long guestNice) {

        static CpuTimes parse(String cpuLine) {
            String[] parts = cpuLine.trim().split("\\s+");
            long[] values = new long[10];
            for (int index = 0; index < values.length; index++) {
                int partIndex = index + 1;
                values[index] = partIndex < parts.length ? Long.parseLong(parts[partIndex]) : 0L;
            }
            return new CpuTimes(
                    values[0],
                    values[1],
                    values[2],
                    values[3],
                    values[4],
                    values[5],
                    values[6],
                    values[7],
                    values[8],
                    values[9]);
        }

        long idleAll() {
            return idle + iowait;
        }

        long total() {
            return user + nice + system + idle + iowait + irq + softirq + steal + guest + guestNice;
        }
    }
}
