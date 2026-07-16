package com.enterprise.testagent.opencode.runtime.process.socket;

import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeMetrics;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;

/**
 * 采集当前 Java 后端进程和所在服务器的资源指标，供 Redis 运行管理快照和历史趋势使用。
 * 内存采集根据操作系统类型选择不同策略，确保 macOS、Linux、Windows 都能获取准确的内存信息。
 */
public class BackendRuntimeMetricsCollector {

    private final SystemMemoryProvider memoryProvider;
    private final OsType osType;
    private final LinuxServerMetricsProvider linuxServerMetricsProvider;
    private final LinuxJavaProcessMetricsProvider linuxJavaProcessMetricsProvider;
    private final Path diskPath;

    private Long lastGcCollectionMillis;
    private Long lastGcCollectionCount;
    private Long lastGcWallTimeMillis;
    private Long lastProcessCpuTimeNanos;
    private Long lastProcessCpuWallTimeNanos;

    public BackendRuntimeMetricsCollector() {
        this(OsType.detect(), Path.of("").toAbsolutePath().normalize());
    }

    public BackendRuntimeMetricsCollector(Path diskPath) {
        this(OsType.detect(), diskPath);
    }

    /**
     * 用于测试的构造函数，允许指定操作系统类型。
     */
    BackendRuntimeMetricsCollector(OsType osType) {
        this(osType, Path.of("").toAbsolutePath().normalize());
    }

    BackendRuntimeMetricsCollector(OsType osType, Path diskPath) {
        this.osType = osType == null ? OsType.UNKNOWN : osType;
        this.memoryProvider = createMemoryProvider(this.osType);
        this.linuxServerMetricsProvider = new LinuxServerMetricsProvider();
        this.linuxJavaProcessMetricsProvider = new LinuxJavaProcessMetricsProvider();
        this.diskPath = diskPath == null ? Path.of("").toAbsolutePath().normalize() : diskPath.toAbsolutePath().normalize();
    }

    private SystemMemoryProvider createMemoryProvider(OsType osType) {
        return switch (osType) {
            case MACOS -> new MacOsMemoryProvider();
            case LINUX -> new LinuxMemoryProvider();
            case WINDOWS -> new WindowsMemoryProvider();
            case UNKNOWN -> new LinuxMemoryProvider(); // 默认使用 Linux 策略
        };
    }

    /**
     * 返回当前 Java 进程运行指标；单个指标不可用时只置空该字段，不阻断心跳。
     */
    public synchronized BackendRuntimeMetrics sample() {
        com.sun.management.OperatingSystemMXBean osBean = operatingSystemBean();
        ServerUsage serverUsage = serverUsage(osBean);
        LinuxJavaProcessMetricsProvider.JavaProcessOsMetrics processMetrics = osType == OsType.LINUX
                ? linuxJavaProcessMetricsProvider.sample()
                : new LinuxJavaProcessMetricsProvider.JavaProcessOsMetrics(null, null, null, null, null, null, null);
        ProcessCpuUsage processCpuUsage = processCpuUsage(osBean, serverUsage.cpuCoreCount());
        JvmMemoryUsage jvmMemoryUsage = jvmMemoryUsage();
        BufferPoolUsage bufferPoolUsage = bufferPoolUsage();
        GcUsage gcUsage = gcUsage();
        ThreadUsage threadUsage = threadUsage();
        return new BackendRuntimeMetrics(
                serverUsage.cpuUsagePercent(),
                serverUsage.cpuCoreCount(),
                serverUsage.loadAverage1m(),
                serverUsage.loadAverage5m(),
                serverUsage.loadAverage15m(),
                serverUsage.memoryTotalBytes(), // memoryMaxBytes 兼容旧字段
                serverUsage.memoryTotalBytes(),
                serverUsage.memoryAvailableBytes(),
                serverUsage.memoryFreeBytes(),
                serverUsage.memoryUsedBytes(),
                serverUsage.memoryUsagePercent(),
                serverUsage.memoryBuffersBytes(),
                serverUsage.memoryCachedBytes(),
                serverUsage.swapTotalBytes(),
                serverUsage.swapFreeBytes(),
                serverUsage.swapUsedBytes(),
                serverUsage.swapUsagePercent(),
                serverUsage.diskTotalBytes(),
                serverUsage.diskAvailableBytes(),
                serverUsage.diskUsedBytes(),
                serverUsage.diskUsagePercent(),
                processCpuUsage.usagePercent(),
                processCpuUsage.coreUsage(),
                processCpuUsage.cpuTimeNanos(),
                processMetrics.residentMemoryBytes(),
                processMetrics.peakResidentMemoryBytes(),
                processMetrics.virtualMemoryBytes(),
                processMetrics.swapBytes(),
                processMetrics.openFileDescriptorCount(),
                processMetrics.maxFileDescriptorCount(),
                jvmMemoryUsage.usedBytes(),
                jvmMemoryUsage.committedBytes(),
                jvmMemoryUsage.maxBytes(),
                jvmMemoryUsage.heapUsedBytes(),
                jvmMemoryUsage.heapCommittedBytes(),
                jvmMemoryUsage.heapMaxBytes(),
                jvmMemoryUsage.nonHeapUsedBytes(),
                jvmMemoryUsage.nonHeapCommittedBytes(),
                jvmMemoryUsage.nonHeapMaxBytes(),
                bufferPoolUsage.directCount(),
                bufferPoolUsage.directUsedBytes(),
                bufferPoolUsage.directCapacityBytes(),
                bufferPoolUsage.mappedCount(),
                bufferPoolUsage.mappedUsedBytes(),
                bufferPoolUsage.mappedCapacityBytes(),
                gcUsage.collectionTimeDeltaMillis(), // jvmGcPauseMillis 兼容旧字段
                gcUsage.collectionTimeDeltaMillis(),
                gcUsage.collectionCountDelta(),
                gcUsage.gcTimePercent(),
                threadUsage.live(),
                threadUsage.daemon(),
                threadUsage.peak(),
                threadUsage.totalStarted());
    }

    private com.sun.management.OperatingSystemMXBean operatingSystemBean() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean) {
            return bean;
        }
        return null;
    }

    private ServerUsage serverUsage(com.sun.management.OperatingSystemMXBean osBean) {
        if (osType == OsType.LINUX) {
            LinuxServerMetricsProvider.LinuxServerMetrics metrics = linuxServerMetricsProvider.sample(diskPath);
            return new ServerUsage(
                    metrics.cpuUsagePercent(),
                    metrics.cpuCoreCount(),
                    metrics.loadAverage1m(),
                    metrics.loadAverage5m(),
                    metrics.loadAverage15m(),
                    metrics.memoryTotalBytes(),
                    metrics.memoryAvailableBytes(),
                    metrics.memoryFreeBytes(),
                    metrics.memoryUsedBytes(),
                    metrics.memoryUsagePercent(),
                    metrics.memoryBuffersBytes(),
                    metrics.memoryCachedBytes(),
                    metrics.swapTotalBytes(),
                    metrics.swapFreeBytes(),
                    metrics.swapUsedBytes(),
                    metrics.swapUsagePercent(),
                    metrics.diskTotalBytes(),
                    metrics.diskAvailableBytes(),
                    metrics.diskUsedBytes(),
                    metrics.diskUsagePercent());
        }
        MemoryInfo memoryInfo = memoryProvider.getMemoryInfo();
        Long memoryTotalBytes = memoryInfo == null ? null : memoryInfo.totalBytes();
        Long memoryAvailableBytes = memoryInfo == null ? null : memoryInfo.availableBytes();
        Long memoryUsedBytes = used(memoryTotalBytes, memoryAvailableBytes);
        DiskUsage diskUsage = diskUsage(diskPath);
        return new ServerUsage(
                cpuUsagePercent(osBean),
                Runtime.getRuntime().availableProcessors(),
                null,
                null,
                null,
                memoryTotalBytes,
                memoryAvailableBytes,
                null,
                memoryUsedBytes,
                percent(memoryUsedBytes, memoryTotalBytes),
                null,
                null,
                null,
                null,
                null,
                null,
                diskUsage.totalBytes(),
                diskUsage.availableBytes(),
                diskUsage.usedBytes(),
                percent(diskUsage.usedBytes(), diskUsage.totalBytes()));
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

    private DiskUsage diskUsage(Path path) {
        java.io.File root = (path == null ? Path.of("").toAbsolutePath().normalize() : path.toAbsolutePath().normalize()).toFile();
        Long totalBytes = positive(root.getTotalSpace());
        Long availableBytes = root.getUsableSpace() >= 0 ? root.getUsableSpace() : null;
        Long usedBytes = used(totalBytes, availableBytes);
        return new DiskUsage(totalBytes, availableBytes, usedBytes);
    }

    private ProcessCpuUsage processCpuUsage(com.sun.management.OperatingSystemMXBean osBean, Integer cpuCoreCount) {
        if (osBean == null) {
            return new ProcessCpuUsage(null, null, null);
        }
        long currentCpuTime = osBean.getProcessCpuTime();
        if (currentCpuTime < 0) {
            return new ProcessCpuUsage(null, null, null);
        }
        long currentWallTime = System.nanoTime();
        Long previousCpuTime = lastProcessCpuTimeNanos;
        Long previousWallTime = lastProcessCpuWallTimeNanos;
        lastProcessCpuTimeNanos = currentCpuTime;
        lastProcessCpuWallTimeNanos = currentWallTime;
        if (previousCpuTime == null || previousWallTime == null) {
            return new ProcessCpuUsage(null, null, currentCpuTime);
        }
        long cpuDelta = currentCpuTime - previousCpuTime;
        long wallDelta = currentWallTime - previousWallTime;
        if (cpuDelta < 0 || wallDelta <= 0) {
            return new ProcessCpuUsage(null, null, currentCpuTime);
        }
        double coreUsage = Math.max(0.0, cpuDelta / (double) wallDelta);
        Double usagePercent = cpuCoreCount == null || cpuCoreCount <= 0
                ? null
                : clamp(coreUsage / cpuCoreCount * 100.0, 0.0, 100.0);
        return new ProcessCpuUsage(usagePercent, coreUsage, currentCpuTime);
    }

    private JvmMemoryUsage jvmMemoryUsage() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        Long heapUsed = positive(heap.getUsed());
        Long heapCommitted = positive(heap.getCommitted());
        Long heapMax = positive(heap.getMax());
        Long nonHeapUsed = positive(nonHeap.getUsed());
        Long nonHeapCommitted = positive(nonHeap.getCommitted());
        Long nonHeapMax = positive(nonHeap.getMax());
        return new JvmMemoryUsage(
                heapUsed,
                heapCommitted,
                heapMax,
                nonHeapUsed,
                nonHeapCommitted,
                nonHeapMax,
                sumNullable(heapUsed, nonHeapUsed),
                sumNullable(heapCommitted, nonHeapCommitted),
                sumNullable(heapMax, nonHeapMax));
    }

    private BufferPoolUsage bufferPoolUsage() {
        long directCount = 0;
        long directUsed = 0;
        long directCapacity = 0;
        long mappedCount = 0;
        long mappedUsed = 0;
        long mappedCapacity = 0;
        for (BufferPoolMXBean bean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            String name = bean.getName() == null ? "" : bean.getName().toLowerCase(java.util.Locale.ROOT);
            long count = nonNegativeOrZero(bean.getCount());
            long used = nonNegativeOrZero(bean.getMemoryUsed());
            long capacity = nonNegativeOrZero(bean.getTotalCapacity());
            if (name.contains("direct")) {
                directCount += count;
                directUsed += used;
                directCapacity += capacity;
            } else if (name.contains("mapped")) {
                mappedCount += count;
                mappedUsed += used;
                mappedCapacity += capacity;
            }
        }
        return new BufferPoolUsage(
                nonNegative(directCount),
                nonNegative(directUsed),
                nonNegative(directCapacity),
                nonNegative(mappedCount),
                nonNegative(mappedUsed),
                nonNegative(mappedCapacity));
    }

    private GcUsage gcUsage() {
        long collectionMillis = 0;
        long collectionCount = 0;
        boolean foundMillis = false;
        boolean foundCount = false;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long collectionTime = bean.getCollectionTime();
            if (collectionTime >= 0) {
                collectionMillis += collectionTime;
                foundMillis = true;
            }
            long count = bean.getCollectionCount();
            if (count >= 0) {
                collectionCount += count;
                foundCount = true;
            }
        }
        long currentWallMillis = System.currentTimeMillis();
        Long previousMillis = lastGcCollectionMillis;
        Long previousCount = lastGcCollectionCount;
        Long previousWallMillis = lastGcWallTimeMillis;
        lastGcCollectionMillis = foundMillis ? collectionMillis : null;
        lastGcCollectionCount = foundCount ? collectionCount : null;
        lastGcWallTimeMillis = currentWallMillis;

        Long millisDelta = previousMillis == null || !foundMillis || collectionMillis < previousMillis
                ? null
                : collectionMillis - previousMillis;
        Long countDelta = previousCount == null || !foundCount || collectionCount < previousCount
                ? null
                : collectionCount - previousCount;
        Double gcTimePercent = null;
        if (millisDelta != null && previousWallMillis != null) {
            long wallDelta = currentWallMillis - previousWallMillis;
            if (wallDelta > 0) {
                gcTimePercent = millisDelta.doubleValue() / wallDelta * 100.0;
            }
        }
        return new GcUsage(millisDelta, countDelta, gcTimePercent);
    }

    private ThreadUsage threadUsage() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        return new ThreadUsage(
                bean.getThreadCount(),
                bean.getDaemonThreadCount(),
                bean.getPeakThreadCount(),
                bean.getTotalStartedThreadCount());
    }

    private static Long positive(long value) {
        return value > 0 ? value : null;
    }

    private static Long nonNegative(long value) {
        return value >= 0 ? value : null;
    }

    private static long nonNegativeOrZero(long value) {
        return value >= 0 ? value : 0;
    }

    private static Long used(Long totalBytes, Long availableBytes) {
        if (totalBytes == null || availableBytes == null) {
            return null;
        }
        return Math.max(0L, totalBytes - Math.min(availableBytes, totalBytes));
    }

    private static Long sumNullable(Long left, Long right) {
        if (left == null && right == null) {
            return null;
        }
        return (left == null ? 0L : left) + (right == null ? 0L : right);
    }

    private static Double percent(Long used, Long max) {
        if (used == null || max == null || max <= 0) {
            return null;
        }
        return used.doubleValue() / max.doubleValue() * 100;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ServerUsage(
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

    private record DiskUsage(Long totalBytes, Long availableBytes, Long usedBytes) {
    }

    private record ProcessCpuUsage(Double usagePercent, Double coreUsage, Long cpuTimeNanos) {
    }

    private record JvmMemoryUsage(
            Long heapUsedBytes,
            Long heapCommittedBytes,
            Long heapMaxBytes,
            Long nonHeapUsedBytes,
            Long nonHeapCommittedBytes,
            Long nonHeapMaxBytes,
            Long usedBytes,
            Long committedBytes,
            Long maxBytes) {
    }

    private record BufferPoolUsage(
            Long directCount,
            Long directUsedBytes,
            Long directCapacityBytes,
            Long mappedCount,
            Long mappedUsedBytes,
            Long mappedCapacityBytes) {
    }

    private record GcUsage(
            Long collectionTimeDeltaMillis,
            Long collectionCountDelta,
            Double gcTimePercent) {
    }

    private record ThreadUsage(
            Integer live,
            Integer daemon,
            Integer peak,
            Long totalStarted) {
    }
}
