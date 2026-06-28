package com.icbc.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeMetrics;
import org.junit.jupiter.api.Test;

class BackendRuntimeMetricsCollectorTest {

    @Test
    void sampleReturnsMetricsForCurrentOs() {
        BackendRuntimeMetricsCollector collector = new BackendRuntimeMetricsCollector();

        BackendRuntimeMetrics metrics = collector.sample();

        // 验证所有指标都能正确采集（不抛异常）
        assertThat(metrics).isNotNull();
        // JVM 指标应该总是能获取
        assertThat(metrics.jvmMemoryUsedBytes()).isNotNull();
        assertThat(metrics.jvmMemoryCommittedBytes()).isNotNull();
        assertThat(metrics.jvmThreadsLive()).isNotNull();
    }

    @Test
    void sampleUsesMacOsProviderWhenOnMacOs() {
        BackendRuntimeMetricsCollector collector = new BackendRuntimeMetricsCollector(OsType.MACOS);

        BackendRuntimeMetrics metrics = collector.sample();

        assertThat(metrics).isNotNull();
        // 在 macOS 上应该能获取到内存信息
        // 如果不在 macOS 上运行，vm_stat 会失败，但会降级到 OperatingSystemMXBean
    }

    @Test
    void sampleUsesLinuxProviderWhenOnLinux() {
        BackendRuntimeMetricsCollector collector = new BackendRuntimeMetricsCollector(OsType.LINUX);

        BackendRuntimeMetrics metrics = collector.sample();

        assertThat(metrics).isNotNull();
    }

    @Test
    void sampleUsesWindowsProviderWhenOnWindows() {
        BackendRuntimeMetricsCollector collector = new BackendRuntimeMetricsCollector(OsType.WINDOWS);

        BackendRuntimeMetrics metrics = collector.sample();

        assertThat(metrics).isNotNull();
    }

    @Test
    void sampleHandlesUnknownOsGracefully() {
        BackendRuntimeMetricsCollector collector = new BackendRuntimeMetricsCollector(OsType.UNKNOWN);

        BackendRuntimeMetrics metrics = collector.sample();

        assertThat(metrics).isNotNull();
    }

    @Test
    void memoryMetricsShouldBeConsistent() {
        BackendRuntimeMetricsCollector collector = new BackendRuntimeMetricsCollector();

        BackendRuntimeMetrics metrics = collector.sample();

        // 如果内存信息可用，已用内存不应超过总内存
        if (metrics.memoryMaxBytes() != null && metrics.memoryUsedBytes() != null) {
            assertThat(metrics.memoryUsedBytes()).isLessThanOrEqualTo(metrics.memoryMaxBytes());
        }
        // 如果使用率可用，应该在合理范围内
        if (metrics.memoryUsagePercent() != null) {
            assertThat(metrics.memoryUsagePercent()).isBetween(0.0, 100.0);
        }
    }

    @Test
    void diskMetricsShouldBeConsistent() {
        BackendRuntimeMetricsCollector collector = new BackendRuntimeMetricsCollector();

        BackendRuntimeMetrics metrics = collector.sample();

        // 磁盘信息应该总是能获取
        assertThat(metrics.diskMaxBytes()).isNotNull().isPositive();
        assertThat(metrics.diskUsedBytes()).isNotNull().isPositive();
        assertThat(metrics.diskUsedBytes()).isLessThanOrEqualTo(metrics.diskMaxBytes());
        assertThat(metrics.diskUsagePercent()).isBetween(0.0, 100.0);
    }

    @Test
    void jvmMetricsShouldBeConsistent() {
        BackendRuntimeMetricsCollector collector = new BackendRuntimeMetricsCollector();

        BackendRuntimeMetrics metrics = collector.sample();

        // JVM 堆内存指标
        assertThat(metrics.jvmMemoryUsedBytes()).isNotNull().isPositive();
        assertThat(metrics.jvmMemoryCommittedBytes()).isNotNull().isPositive();
        assertThat(metrics.jvmMemoryUsedBytes()).isLessThanOrEqualTo(metrics.jvmMemoryCommittedBytes());
        // 线程数
        assertThat(metrics.jvmThreadsLive()).isNotNull().isPositive();
    }
}