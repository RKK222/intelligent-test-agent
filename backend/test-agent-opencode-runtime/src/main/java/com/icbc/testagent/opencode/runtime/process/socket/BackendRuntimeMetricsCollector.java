package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeMetrics;
import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.Path;

/**
 * 采集当前 Java 后端进程和所在服务器的资源指标，供 Redis 运行管理快照和历史趋势使用。
 */
public class BackendRuntimeMetricsCollector {

    private Long lastGcCollectionMillis;

    /**
     * 返回当前 Java 进程运行指标；单个指标不可用时只置空该字段，不阻断心跳。
     */
    public synchronized BackendRuntimeMetrics sample() {
        com.sun.management.OperatingSystemMXBean osBean = operatingSystemBean();
        Double cpuUsagePercent = cpuUsagePercent(osBean);
        Long memoryMaxBytes = osBean == null ? null : positive(osBean.getTotalMemorySize());
        Long memoryFreeBytes = osBean == null ? null : positive(osBean.getFreeMemorySize());
        Long memoryUsedBytes = memoryMaxBytes == null || memoryFreeBytes == null ? null : Math.max(0, memoryMaxBytes - memoryFreeBytes);
        Double memoryUsagePercent = percent(memoryUsedBytes, memoryMaxBytes);
        DiskUsage diskUsage = diskUsage();
        JvmMemoryUsage jvmMemoryUsage = jvmMemoryUsage();
        return new BackendRuntimeMetrics(
                cpuUsagePercent,
                memoryMaxBytes,
                memoryUsedBytes,
                memoryUsagePercent,
                diskUsage.maxBytes(),
                diskUsage.usedBytes(),
                percent(diskUsage.usedBytes(), diskUsage.maxBytes()),
                jvmMemoryUsage.usedBytes(),
                jvmMemoryUsage.committedBytes(),
                jvmMemoryUsage.maxBytes(),
                gcPauseDeltaMillis(),
                ManagementFactory.getThreadMXBean().getThreadCount());
    }

    private com.sun.management.OperatingSystemMXBean operatingSystemBean() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean) {
            return bean;
        }
        return null;
    }

    private Double cpuUsagePercent(com.sun.management.OperatingSystemMXBean osBean) {
        if (osBean == null) {
            return null;
        }
        double cpuLoad = osBean.getCpuLoad();
        if (cpuLoad < 0) {
            return null;
        }
        return cpuLoad * 100;
    }

    private DiskUsage diskUsage() {
        File root = Path.of("").toAbsolutePath().normalize().toFile();
        long total = root.getTotalSpace();
        long free = root.getUsableSpace();
        Long maxBytes = positive(total);
        Long usedBytes = maxBytes == null ? null : Math.max(0, total - Math.max(0, free));
        return new DiskUsage(maxBytes, usedBytes);
    }

    private JvmMemoryUsage jvmMemoryUsage() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        long used = positiveOrZero(heap.getUsed()) + positiveOrZero(nonHeap.getUsed());
        long committed = positiveOrZero(heap.getCommitted()) + positiveOrZero(nonHeap.getCommitted());
        long max = positiveOrZero(heap.getMax()) + positiveOrZero(nonHeap.getMax());
        return new JvmMemoryUsage(
                positive(used),
                positive(committed),
                max > 0 ? max : null);
    }

    private Long gcPauseDeltaMillis() {
        long current = 0;
        boolean found = false;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long collectionTime = bean.getCollectionTime();
            if (collectionTime >= 0) {
                current += collectionTime;
                found = true;
            }
        }
        if (!found) {
            return null;
        }
        Long previous = lastGcCollectionMillis;
        lastGcCollectionMillis = current;
        if (previous == null || current < previous) {
            return null;
        }
        return current - previous;
    }

    private Long positive(long value) {
        return value > 0 ? value : null;
    }

    private long positiveOrZero(long value) {
        return value > 0 ? value : 0;
    }

    private Double percent(Long used, Long max) {
        if (used == null || max == null || max <= 0) {
            return null;
        }
        return used.doubleValue() / max.doubleValue() * 100;
    }

    private record DiskUsage(Long maxBytes, Long usedBytes) {
    }

    private record JvmMemoryUsage(Long usedBytes, Long committedBytes, Long maxBytes) {
    }
}
