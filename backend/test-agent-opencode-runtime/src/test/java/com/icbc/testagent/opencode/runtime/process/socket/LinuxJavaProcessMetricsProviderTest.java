package com.icbc.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinuxJavaProcessMetricsProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void samplesProcessMemoryAndFileDescriptorsFromProcSelf() throws Exception {
        Path status = tempDir.resolve("status");
        Path fd = Files.createDirectory(tempDir.resolve("fd"));
        Files.writeString(status, """
                Name:\tjava
                VmSize:\t  2000 kB
                VmHWM:\t   900 kB
                VmRSS:\t   800 kB
                VmSwap:\t   50 kB
                Threads:\t17
                """);
        Files.createFile(fd.resolve("0"));
        Files.createFile(fd.resolve("1"));
        Files.createFile(fd.resolve("2"));

        LinuxJavaProcessMetricsProvider.JavaProcessOsMetrics metrics =
                new LinuxJavaProcessMetricsProvider(status, fd, false).sample();

        assertThat(metrics.residentMemoryBytes()).isEqualTo(800L * 1024L);
        assertThat(metrics.peakResidentMemoryBytes()).isEqualTo(900L * 1024L);
        assertThat(metrics.virtualMemoryBytes()).isEqualTo(2000L * 1024L);
        assertThat(metrics.swapBytes()).isEqualTo(50L * 1024L);
        assertThat(metrics.processThreads()).isEqualTo(17L);
        assertThat(metrics.openFileDescriptorCount()).isEqualTo(3L);
        assertThat(metrics.maxFileDescriptorCount()).isNull();
    }
}
