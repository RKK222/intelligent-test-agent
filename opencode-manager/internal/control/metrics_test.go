package control

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestLinuxRuntimeMetricsCollectorReadsCgroupV2AndLeavesRatesEmptyOnFirstSample(t *testing.T) {
	root := t.TempDir()
	cgroupRoot := filepath.Join(root, "sys/fs/cgroup")
	procRoot := filepath.Join(root, "proc")
	mustWrite(t, filepath.Join(cgroupRoot, "memory.max"), "1024\n")
	mustWrite(t, filepath.Join(cgroupRoot, "memory.current"), "512\n")
	mustWrite(t, filepath.Join(cgroupRoot, "cpu.max"), "100000 100000\n")
	mustWrite(t, filepath.Join(cgroupRoot, "cpu.stat"), "usage_usec 1000000\n")
	mustWrite(t, filepath.Join(cgroupRoot, "io.stat"), "8:0 rbytes=1024 wbytes=2048\n")
	mustWrite(t, filepath.Join(procRoot, "meminfo"), "MemTotal:       4096 kB\n")
	collector := &LinuxRuntimeMetricsCollector{
		procRoot:   procRoot,
		cgroupRoot: cgroupRoot,
		now:        func() time.Time { return time.Unix(100, 0) },
	}

	sample := collector.Sample()

	if sample.MemoryMaxBytes == nil || *sample.MemoryMaxBytes != 1024 {
		t.Fatalf("expected memory max from cgroup v2, got %#v", sample.MemoryMaxBytes)
	}
	if sample.MemoryUsagePercent == nil || *sample.MemoryUsagePercent != 50 {
		t.Fatalf("expected memory percent from cgroup v2, got %#v", sample.MemoryUsagePercent)
	}
	if sample.CPUUsagePercent != nil {
		t.Fatalf("expected first cpu sample to be empty, got %#v", sample.CPUUsagePercent)
	}
	if sample.DiskReadBytesPerSecond != nil || sample.DiskWriteBytesPerSecond != nil {
		t.Fatalf("expected first io rates to be empty, got read=%#v write=%#v", sample.DiskReadBytesPerSecond, sample.DiskWriteBytesPerSecond)
	}
}

func TestLinuxRuntimeMetricsCollectorComputesCgroupV2CpuAndIORates(t *testing.T) {
	root := t.TempDir()
	cgroupRoot := filepath.Join(root, "sys/fs/cgroup")
	mustWrite(t, filepath.Join(cgroupRoot, "memory.max"), "1024\n")
	mustWrite(t, filepath.Join(cgroupRoot, "memory.current"), "512\n")
	mustWrite(t, filepath.Join(cgroupRoot, "cpu.max"), "100000 100000\n")
	mustWrite(t, filepath.Join(cgroupRoot, "cpu.stat"), "usage_usec 1000000\n")
	mustWrite(t, filepath.Join(cgroupRoot, "io.stat"), "8:0 rbytes=1024 wbytes=2048\n")
	times := []time.Time{time.Unix(100, 0), time.Unix(110, 0)}
	collector := &LinuxRuntimeMetricsCollector{
		cgroupRoot: cgroupRoot,
		now: func() time.Time {
			next := times[0]
			times = times[1:]
			return next
		},
	}
	_ = collector.Sample()
	mustWrite(t, filepath.Join(cgroupRoot, "cpu.stat"), "usage_usec 1500000\n")
	mustWrite(t, filepath.Join(cgroupRoot, "io.stat"), "8:0 rbytes=2048 wbytes=4096\n")

	sample := collector.Sample()

	if sample.CPUUsagePercent == nil || *sample.CPUUsagePercent != 5 {
		t.Fatalf("expected 5%% cpu usage, got %#v", sample.CPUUsagePercent)
	}
	if sample.DiskReadBytesPerSecond == nil || *sample.DiskReadBytesPerSecond != 102.4 {
		t.Fatalf("expected read rate from io.stat delta, got %#v", sample.DiskReadBytesPerSecond)
	}
	if sample.DiskWriteBytesPerSecond == nil || *sample.DiskWriteBytesPerSecond != 204.8 {
		t.Fatalf("expected write rate from io.stat delta, got %#v", sample.DiskWriteBytesPerSecond)
	}
}

func TestLinuxRuntimeMetricsCollectorReadsCgroupV1Memory(t *testing.T) {
	root := t.TempDir()
	cgroupRoot := filepath.Join(root, "sys/fs/cgroup")
	mustWrite(t, filepath.Join(cgroupRoot, "memory/memory.limit_in_bytes"), "2048\n")
	mustWrite(t, filepath.Join(cgroupRoot, "memory/memory.usage_in_bytes"), "1024\n")
	collector := &LinuxRuntimeMetricsCollector{
		cgroupRoot: cgroupRoot,
		now:        func() time.Time { return time.Unix(100, 0) },
	}

	sample := collector.Sample()

	if sample.MemoryMaxBytes == nil || *sample.MemoryMaxBytes != 2048 {
		t.Fatalf("expected memory max from cgroup v1, got %#v", sample.MemoryMaxBytes)
	}
	if sample.MemoryUsedBytes == nil || *sample.MemoryUsedBytes != 1024 {
		t.Fatalf("expected memory used from cgroup v1, got %#v", sample.MemoryUsedBytes)
	}
}

func mustWrite(t *testing.T, path string, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatalf("mkdir failed: %v", err)
	}
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatalf("write failed: %v", err)
	}
}
