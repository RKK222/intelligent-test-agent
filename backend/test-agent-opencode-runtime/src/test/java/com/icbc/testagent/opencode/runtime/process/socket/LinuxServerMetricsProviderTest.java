package com.icbc.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinuxServerMetricsProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void samplesLinuxProcMetricsWithMemAvailableAndCpuDelta() throws Exception {
        Path procRoot = Files.createDirectory(tempDir.resolve("proc"));
        Files.writeString(procRoot.resolve("meminfo"), """
                MemTotal:       1000 kB
                MemFree:         100 kB
                MemAvailable:    400 kB
                Buffers:          50 kB
                Cached:          200 kB
                SwapTotal:       500 kB
                SwapFree:        300 kB
                """);
        Files.writeString(procRoot.resolve("loadavg"), "1.25 2.50 3.75 1/100 12345\n");
        Files.writeString(procRoot.resolve("stat"), "cpu  100 0 100 800 0 0 0 0 0 0\n");

        LinuxServerMetricsProvider provider = new LinuxServerMetricsProvider(procRoot);

        LinuxServerMetricsProvider.LinuxServerMetrics first = provider.sample(tempDir);
        Files.writeString(procRoot.resolve("stat"), "cpu  150 0 150 900 0 0 0 0 0 0\n");
        LinuxServerMetricsProvider.LinuxServerMetrics second = provider.sample(tempDir);

        assertThat(first.cpuUsagePercent()).isNull();
        assertThat(second.cpuUsagePercent()).isEqualTo(50.0);
        assertThat(second.cpuCoreCount()).isPositive();
        assertThat(second.loadAverage1m()).isEqualTo(1.25);
        assertThat(second.loadAverage5m()).isEqualTo(2.50);
        assertThat(second.loadAverage15m()).isEqualTo(3.75);
        assertThat(second.memoryTotalBytes()).isEqualTo(1000L * 1024L);
        assertThat(second.memoryAvailableBytes()).isEqualTo(400L * 1024L);
        assertThat(second.memoryUsedBytes()).isEqualTo(600L * 1024L);
        assertThat(second.memoryUsagePercent()).isEqualTo(60.0);
        assertThat(second.memoryFreeBytes()).isEqualTo(100L * 1024L);
        assertThat(second.memoryBuffersBytes()).isEqualTo(50L * 1024L);
        assertThat(second.memoryCachedBytes()).isEqualTo(200L * 1024L);
        assertThat(second.swapUsedBytes()).isEqualTo(200L * 1024L);
        assertThat(second.swapUsagePercent()).isEqualTo(40.0);
        assertThat(second.diskTotalBytes()).isNotNull();
        assertThat(second.diskAvailableBytes()).isNotNull();
    }

    @Test
    void fallsBackWhenMemAvailableIsMissing() throws Exception {
        Path procRoot = Files.createDirectory(tempDir.resolve("proc-fallback"));
        Files.writeString(procRoot.resolve("meminfo"), """
                MemTotal:       1000 kB
                MemFree:         100 kB
                Buffers:          50 kB
                Cached:          200 kB
                SReclaimable:     30 kB
                Shmem:            20 kB
                """);
        Files.writeString(procRoot.resolve("loadavg"), "0.00 0.00 0.00 1/100 12345\n");
        Files.writeString(procRoot.resolve("stat"), "cpu  100 0 100 800 0 0 0 0 0 0\n");

        LinuxServerMetricsProvider.LinuxServerMetrics metrics = new LinuxServerMetricsProvider(procRoot).sample(tempDir);

        assertThat(metrics.memoryAvailableBytes()).isEqualTo(360L * 1024L);
        assertThat(metrics.memoryUsedBytes()).isEqualTo(640L * 1024L);
    }
}
