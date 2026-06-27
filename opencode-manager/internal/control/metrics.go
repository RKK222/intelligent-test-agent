package control

import (
	"log"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/shirou/gopsutil/v4/mem"
	gopsprocess "github.com/shirou/gopsutil/v4/process"
)

const (
	MetricsSourceCgroup      = "cgroup"
	MetricsSourceProcess     = "process"
	MetricsSourceUnavailable = "unavailable"
)

// RuntimeMetricsCollector 采集当前 manager 所在容器的资源指标；读取失败时返回空字段，不阻塞心跳。
type RuntimeMetricsCollector interface {
	Sample() RuntimeMetricsSample
}

// RuntimeMetricsSample 是 manager 心跳上报的容器资源快照，nil 表示当前环境无法安全采集该指标；MetricsSource 说明指标来源。
type RuntimeMetricsSample struct {
	CPUUsagePercent         *float64 `json:"cpuUsagePercent,omitempty"`
	MemoryMaxBytes          *int64   `json:"memoryMaxBytes,omitempty"`
	MemoryUsedBytes         *int64   `json:"memoryUsedBytes,omitempty"`
	MemoryUsagePercent      *float64 `json:"memoryUsagePercent,omitempty"`
	DiskReadBytesPerSecond  *float64 `json:"diskReadBytesPerSecond,omitempty"`
	DiskWriteBytesPerSecond *float64 `json:"diskWriteBytesPerSecond,omitempty"`
	MetricsSource           string   `json:"metricsSource,omitempty"`
}

type processMetricsAdapter interface {
	Sample() (RuntimeMetricsSample, bool, string)
}

// LinuxRuntimeMetricsCollector 通过 cgroup/procfs 只读文件采集容器指标；失败时降级采集当前 manager 进程指标。
type LinuxRuntimeMetricsCollector struct {
	mu                 sync.Mutex
	procRoot           string
	cgroupRoot         string
	now                func() time.Time
	processMetrics     processMetricsAdapter
	previousCPU        *resourceUsageReading
	previousIO         *diskIOReading
	lastDiagnosticAt   time.Time
	diagnosticInterval time.Duration
}

type resourceUsageReading struct {
	value    float64
	capacity float64
	at       time.Time
}

type diskIOReading struct {
	readBytes  int64
	writeBytes int64
	at         time.Time
}

type linuxCgroupPaths struct {
	v2      string
	memory  string
	cpu     string
	cpuacct string
	blkio   string
}

type cgroupMount struct {
	mountPoint  string
	fsType      string
	controllers []string
}

// NewRuntimeMetricsCollector 按当前操作系统选择采集器；Linux 优先 cgroup，macOS/Windows 使用进程指标。
func NewRuntimeMetricsCollector() RuntimeMetricsCollector {
	return newRuntimeMetricsCollectorForGOOS(runtime.GOOS, newGopsutilProcessMetricsAdapter())
}

func newRuntimeMetricsCollectorForGOOS(goos string, adapter processMetricsAdapter) RuntimeMetricsCollector {
	if adapter == nil {
		adapter = newGopsutilProcessMetricsAdapter()
	}
	switch goos {
	case "linux":
		return &LinuxRuntimeMetricsCollector{
			procRoot:       "/proc",
			cgroupRoot:     "/sys/fs/cgroup",
			now:            time.Now,
			processMetrics: adapter,
		}
	case "darwin", "windows":
		return &processRuntimeMetricsCollector{
			goos:           goos,
			processMetrics: adapter,
		}
	default:
		return unavailableRuntimeMetricsCollector{goos: goos}
	}
}

// NewLinuxRuntimeMetricsCollector 创建生产采集器，默认读取 /proc 与 /sys/fs/cgroup。
func NewLinuxRuntimeMetricsCollector() *LinuxRuntimeMetricsCollector {
	return &LinuxRuntimeMetricsCollector{
		procRoot:       "/proc",
		cgroupRoot:     "/sys/fs/cgroup",
		now:            time.Now,
		processMetrics: newGopsutilProcessMetricsAdapter(),
	}
}

// Sample 返回当前资源指标；CPU 与磁盘 IO 需要两次采样才能计算速率，首个样本对应字段为空。
func (c *LinuxRuntimeMetricsCollector) Sample() RuntimeMetricsSample {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.withDefaults()
	now := c.now().UTC()
	sample := RuntimeMetricsSample{}
	cgroupSampled := false
	if max, used, ok := c.memory(); ok {
		cgroupSampled = true
		sample.MemoryMaxBytes = int64Pointer(max)
		sample.MemoryUsedBytes = int64Pointer(used)
		if max > 0 {
			sample.MemoryUsagePercent = float64Pointer(float64(used) / float64(max) * 100)
		}
	}
	if current, ok := c.cpu(now); ok {
		cgroupSampled = true
		if c.previousCPU != nil {
			elapsed := current.at.Sub(c.previousCPU.at).Seconds()
			if elapsed > 0 && current.value >= c.previousCPU.value && current.capacity > 0 {
				sample.CPUUsagePercent = float64Pointer((current.value - c.previousCPU.value) / elapsed / current.capacity * 100)
			}
		}
		c.previousCPU = &current
	}
	if current, ok := c.diskIO(now); ok {
		cgroupSampled = true
		if c.previousIO != nil {
			elapsed := current.at.Sub(c.previousIO.at).Seconds()
			if elapsed > 0 {
				if current.readBytes >= c.previousIO.readBytes {
					sample.DiskReadBytesPerSecond = float64Pointer(float64(current.readBytes-c.previousIO.readBytes) / elapsed)
				}
				if current.writeBytes >= c.previousIO.writeBytes {
					sample.DiskWriteBytesPerSecond = float64Pointer(float64(current.writeBytes-c.previousIO.writeBytes) / elapsed)
				}
			}
		}
		c.previousIO = &current
	}
	if cgroupSampled {
		sample.MetricsSource = MetricsSourceCgroup
		return sample
	}
	return c.processFallback(now, "cgroup metrics unavailable")
}

func (c *LinuxRuntimeMetricsCollector) processFallback(now time.Time, reason string) RuntimeMetricsSample {
	if c.processMetrics != nil {
		if sample, ok, fallbackReason := c.processMetrics.Sample(); ok {
			if sample.MetricsSource == "" {
				sample.MetricsSource = MetricsSourceProcess
			}
			c.logDiagnostic(now, "linux", sample.MetricsSource, reason)
			return sample
		} else if fallbackReason != "" {
			reason = reason + "; " + fallbackReason
		}
	}
	c.logDiagnostic(now, "linux", MetricsSourceUnavailable, reason)
	return RuntimeMetricsSample{MetricsSource: MetricsSourceUnavailable}
}

func (c *LinuxRuntimeMetricsCollector) logDiagnostic(now time.Time, collector string, source string, reason string) {
	interval := c.diagnosticInterval
	if interval <= 0 {
		interval = 5 * time.Minute
	}
	if !c.lastDiagnosticAt.IsZero() && now.Sub(c.lastDiagnosticAt) < interval {
		return
	}
	c.lastDiagnosticAt = now
	log.Printf("runtime metrics collector=%s goos=%s source=%s reason=%s", collector, runtime.GOOS, source, reason)
}

func (c *LinuxRuntimeMetricsCollector) withDefaults() {
	if c.procRoot == "" {
		c.procRoot = "/proc"
	}
	if c.cgroupRoot == "" {
		c.cgroupRoot = "/sys/fs/cgroup"
	}
	if c.now == nil {
		c.now = time.Now
	}
	if c.processMetrics == nil {
		c.processMetrics = newGopsutilProcessMetricsAdapter()
	}
}

func (c *LinuxRuntimeMetricsCollector) memory() (int64, int64, bool) {
	paths := c.cgroupPaths()
	if paths.v2 != "" {
		if used, usedOK := readCgroupNumber(filepath.Join(paths.v2, "memory.current")); usedOK {
			if max, ok := readCgroupNumber(filepath.Join(paths.v2, "memory.max")); ok {
				return max, used, true
			}
			if max, ok := c.procMemTotal(); ok {
				return max, used, true
			}
		}
	}
	if paths.memory != "" {
		if max, ok := readCgroupNumber(filepath.Join(paths.memory, "memory.limit_in_bytes")); ok {
			if used, usedOK := readCgroupNumber(filepath.Join(paths.memory, "memory.usage_in_bytes")); usedOK {
				return max, used, true
			}
		}
	}
	if max, ok := readCgroupNumber(filepath.Join(c.cgroupRoot, "memory.max")); ok {
		if used, usedOK := readCgroupNumber(filepath.Join(c.cgroupRoot, "memory.current")); usedOK {
			return max, used, true
		}
	}
	if max, ok := readCgroupNumber(filepath.Join(c.cgroupRoot, "memory", "memory.limit_in_bytes")); ok {
		if used, usedOK := readCgroupNumber(filepath.Join(c.cgroupRoot, "memory", "memory.usage_in_bytes")); usedOK {
			return max, used, true
		}
	}
	return 0, 0, false
}

func (c *LinuxRuntimeMetricsCollector) cpu(now time.Time) (resourceUsageReading, bool) {
	paths := c.cgroupPaths()
	if paths.v2 != "" {
		if usage, ok := readCgroupKeyValue(filepath.Join(paths.v2, "cpu.stat"), "usage_usec"); ok {
			return resourceUsageReading{
				value:    float64(usage) / 1_000_000,
				capacity: c.cgroupV2CPUCapacity(paths.v2),
				at:       now,
			}, true
		}
	}
	if paths.cpuacct != "" {
		if usage, ok := readCgroupNumber(filepath.Join(paths.cpuacct, "cpuacct.usage")); ok {
			return resourceUsageReading{
				value:    float64(usage) / 1_000_000_000,
				capacity: c.cgroupV1CPUCapacity(firstNonEmpty(paths.cpu, paths.cpuacct)),
				at:       now,
			}, true
		}
	}
	if usage, ok := readCgroupKeyValue(filepath.Join(c.cgroupRoot, "cpu.stat"), "usage_usec"); ok {
		return resourceUsageReading{
			value:    float64(usage) / 1_000_000,
			capacity: c.cgroupV2CPUCapacity(c.cgroupRoot),
			at:       now,
		}, true
	}
	if usage, ok := readCgroupNumber(filepath.Join(c.cgroupRoot, "cpuacct", "cpuacct.usage")); ok {
		return resourceUsageReading{
			value:    float64(usage) / 1_000_000_000,
			capacity: c.cgroupV1CPUCapacity(filepath.Join(c.cgroupRoot, "cpu")),
			at:       now,
		}, true
	}
	return resourceUsageReading{}, false
}

func (c *LinuxRuntimeMetricsCollector) cgroupV2CPUCapacity(path string) float64 {
	payload, err := os.ReadFile(filepath.Join(path, "cpu.max"))
	if err != nil {
		return float64(runtime.NumCPU())
	}
	parts := strings.Fields(string(payload))
	if len(parts) < 2 || parts[0] == "max" {
		return float64(runtime.NumCPU())
	}
	quota, quotaErr := strconv.ParseFloat(parts[0], 64)
	period, periodErr := strconv.ParseFloat(parts[1], 64)
	if quotaErr != nil || periodErr != nil || quota <= 0 || period <= 0 {
		return float64(runtime.NumCPU())
	}
	return quota / period
}

func (c *LinuxRuntimeMetricsCollector) cgroupV1CPUCapacity(path string) float64 {
	quota, quotaOK := readCgroupNumber(filepath.Join(path, "cpu.cfs_quota_us"))
	period, periodOK := readCgroupNumber(filepath.Join(path, "cpu.cfs_period_us"))
	if !quotaOK || !periodOK || quota <= 0 || period <= 0 {
		return float64(runtime.NumCPU())
	}
	return float64(quota) / float64(period)
}

func (c *LinuxRuntimeMetricsCollector) diskIO(now time.Time) (diskIOReading, bool) {
	paths := c.cgroupPaths()
	if paths.v2 != "" {
		if readBytes, writeBytes, ok := readCgroupV2IO(filepath.Join(paths.v2, "io.stat")); ok {
			return diskIOReading{readBytes: readBytes, writeBytes: writeBytes, at: now}, true
		}
	}
	if paths.blkio != "" {
		if readBytes, writeBytes, ok := readCgroupV1IO(filepath.Join(paths.blkio, "blkio.throttle.io_service_bytes")); ok {
			return diskIOReading{readBytes: readBytes, writeBytes: writeBytes, at: now}, true
		}
	}
	if readBytes, writeBytes, ok := readCgroupV2IO(filepath.Join(c.cgroupRoot, "io.stat")); ok {
		return diskIOReading{readBytes: readBytes, writeBytes: writeBytes, at: now}, true
	}
	if readBytes, writeBytes, ok := readCgroupV1IO(filepath.Join(c.cgroupRoot, "blkio", "blkio.throttle.io_service_bytes")); ok {
		return diskIOReading{readBytes: readBytes, writeBytes: writeBytes, at: now}, true
	}
	return diskIOReading{}, false
}

func (c *LinuxRuntimeMetricsCollector) cgroupPaths() linuxCgroupPaths {
	cgroupsByController, err := parseOwnCgroupFile(filepath.Join(c.procRoot, "self", "cgroup"))
	if err != nil {
		return linuxCgroupPaths{}
	}
	mounts := parseCgroupMountinfo(filepath.Join(c.procRoot, "self", "mountinfo"))
	if v2Path, ok := cgroupsByController[""]; ok {
		mountPoint := c.cgroupRoot
		for _, mount := range mounts {
			if mount.fsType == "cgroup2" {
				mountPoint = mount.mountPoint
				break
			}
		}
		return linuxCgroupPaths{v2: joinCgroupPath(mountPoint, v2Path)}
	}
	return linuxCgroupPaths{
		memory:  joinCgroupPath(controllerMountPoint(mounts, "memory", filepath.Join(c.cgroupRoot, "memory")), cgroupsByController["memory"]),
		cpu:     joinCgroupPath(controllerMountPoint(mounts, "cpu", filepath.Join(c.cgroupRoot, "cpu")), cgroupsByController["cpu"]),
		cpuacct: joinCgroupPath(controllerMountPoint(mounts, "cpuacct", filepath.Join(c.cgroupRoot, "cpuacct")), cgroupsByController["cpuacct"]),
		blkio:   joinCgroupPath(controllerMountPoint(mounts, "blkio", filepath.Join(c.cgroupRoot, "blkio")), cgroupsByController["blkio"]),
	}
}

func parseCgroupMountinfo(path string) []cgroupMount {
	payload, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	mounts := []cgroupMount{}
	for _, line := range strings.Split(string(payload), "\n") {
		if strings.TrimSpace(line) == "" {
			continue
		}
		parts := strings.SplitN(line, " - ", 2)
		if len(parts) != 2 {
			continue
		}
		preFields := strings.Fields(parts[0])
		postFields := strings.Fields(parts[1])
		if len(preFields) < 5 || len(postFields) < 3 {
			continue
		}
		fsType := postFields[0]
		if fsType != "cgroup" && fsType != "cgroup2" {
			continue
		}
		mounts = append(mounts, cgroupMount{
			mountPoint:  unescapeMountinfoPath(preFields[4]),
			fsType:      fsType,
			controllers: cgroupControllers(postFields[2]),
		})
	}
	return mounts
}

func cgroupControllers(options string) []string {
	known := map[string]bool{"memory": true, "cpu": true, "cpuacct": true, "blkio": true}
	controllers := []string{}
	for _, option := range strings.Split(options, ",") {
		if known[option] {
			controllers = append(controllers, option)
		}
	}
	return controllers
}

func controllerMountPoint(mounts []cgroupMount, controller string, fallback string) string {
	for _, mount := range mounts {
		if mount.fsType != "cgroup" {
			continue
		}
		for _, candidate := range mount.controllers {
			if candidate == controller {
				return mount.mountPoint
			}
		}
	}
	return fallback
}

func joinCgroupPath(mountPoint string, cgroupPath string) string {
	if mountPoint == "" {
		return ""
	}
	trimmed := strings.TrimPrefix(cgroupPath, "/")
	if trimmed == "" {
		return mountPoint
	}
	return filepath.Join(mountPoint, trimmed)
}

func unescapeMountinfoPath(path string) string {
	replacer := strings.NewReplacer(`\040`, " ", `\011`, "\t", `\012`, "\n", `\134`, `\`)
	return replacer.Replace(path)
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}

func (c *LinuxRuntimeMetricsCollector) procMemTotal() (int64, bool) {
	payload, err := os.ReadFile(filepath.Join(c.procRoot, "meminfo"))
	if err != nil {
		return 0, false
	}
	for _, line := range strings.Split(string(payload), "\n") {
		fields := strings.Fields(line)
		if len(fields) >= 2 && strings.TrimSuffix(fields[0], ":") == "MemTotal" {
			value, err := strconv.ParseInt(fields[1], 10, 64)
			if err == nil {
				return value * 1024, true
			}
		}
	}
	return 0, false
}

type processRuntimeMetricsCollector struct {
	goos               string
	processMetrics     processMetricsAdapter
	mu                 sync.Mutex
	lastDiagnosticAt   time.Time
	diagnosticInterval time.Duration
}

func (c *processRuntimeMetricsCollector) Sample() RuntimeMetricsSample {
	c.mu.Lock()
	defer c.mu.Unlock()
	now := time.Now().UTC()
	if c.processMetrics != nil {
		if sample, ok, reason := c.processMetrics.Sample(); ok {
			if sample.MetricsSource == "" {
				sample.MetricsSource = MetricsSourceProcess
			}
			c.logDiagnostic(now, sample.MetricsSource, reason)
			return sample
		}
	}
	c.logDiagnostic(now, MetricsSourceUnavailable, "process metrics unavailable")
	return RuntimeMetricsSample{MetricsSource: MetricsSourceUnavailable}
}

func (c *processRuntimeMetricsCollector) logDiagnostic(now time.Time, source string, reason string) {
	interval := c.diagnosticInterval
	if interval <= 0 {
		interval = 5 * time.Minute
	}
	if !c.lastDiagnosticAt.IsZero() && now.Sub(c.lastDiagnosticAt) < interval {
		return
	}
	c.lastDiagnosticAt = now
	log.Printf("runtime metrics collector=process goos=%s source=%s reason=%s", c.goos, source, reason)
}

type unavailableRuntimeMetricsCollector struct {
	goos string
}

func (c unavailableRuntimeMetricsCollector) Sample() RuntimeMetricsSample {
	log.Printf("runtime metrics collector=unavailable goos=%s source=%s reason=unsupported os", c.goos, MetricsSourceUnavailable)
	return RuntimeMetricsSample{MetricsSource: MetricsSourceUnavailable}
}

type gopsutilProcessMetricsAdapter struct {
	mu      sync.Mutex
	process *gopsprocess.Process
}

func newGopsutilProcessMetricsAdapter() *gopsutilProcessMetricsAdapter {
	return &gopsutilProcessMetricsAdapter{}
}

func (a *gopsutilProcessMetricsAdapter) Sample() (RuntimeMetricsSample, bool, string) {
	a.mu.Lock()
	defer a.mu.Unlock()
	proc, err := a.currentProcess()
	if err != nil {
		return RuntimeMetricsSample{}, false, "process handle unavailable: " + err.Error()
	}
	sample := RuntimeMetricsSample{MetricsSource: MetricsSourceProcess}
	collected := false
	if cpuPercent, err := proc.CPUPercent(); err == nil {
		sample.CPUUsagePercent = float64Pointer(cpuPercent)
		collected = true
	}
	if memoryInfo, err := proc.MemoryInfo(); err == nil && memoryInfo != nil {
		used := int64(memoryInfo.RSS)
		sample.MemoryUsedBytes = int64Pointer(used)
		collected = true
		if virtualMemory, err := mem.VirtualMemory(); err == nil && virtualMemory != nil && virtualMemory.Total > 0 {
			max := int64(virtualMemory.Total)
			sample.MemoryMaxBytes = int64Pointer(max)
			sample.MemoryUsagePercent = float64Pointer(float64(used) / float64(max) * 100)
		}
	}
	if !collected {
		return RuntimeMetricsSample{}, false, "gopsutil returned no cpu or memory fields"
	}
	return sample, true, ""
}

func (a *gopsutilProcessMetricsAdapter) currentProcess() (*gopsprocess.Process, error) {
	if a.process != nil {
		return a.process, nil
	}
	proc, err := gopsprocess.NewProcess(int32(os.Getpid()))
	if err != nil {
		return nil, err
	}
	a.process = proc
	return proc, nil
}

func readCgroupNumber(path string) (int64, bool) {
	payload, err := os.ReadFile(path)
	if err != nil {
		return 0, false
	}
	value := strings.TrimSpace(string(payload))
	if value == "" || value == "max" {
		return 0, false
	}
	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil || parsed < 0 || parsed > 1<<60 {
		return 0, false
	}
	return parsed, true
}

func readCgroupKeyValue(path string, key string) (int64, bool) {
	payload, err := os.ReadFile(path)
	if err != nil {
		return 0, false
	}
	for _, line := range strings.Split(string(payload), "\n") {
		fields := strings.Fields(line)
		if len(fields) == 2 && fields[0] == key {
			parsed, err := strconv.ParseInt(fields[1], 10, 64)
			if err == nil {
				return parsed, true
			}
		}
	}
	return 0, false
}

func readCgroupV2IO(path string) (int64, int64, bool) {
	payload, err := os.ReadFile(path)
	if err != nil {
		return 0, 0, false
	}
	var readBytes int64
	var writeBytes int64
	var found bool
	for _, line := range strings.Split(string(payload), "\n") {
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		for _, field := range fields[1:] {
			keyValue := strings.SplitN(field, "=", 2)
			if len(keyValue) != 2 {
				continue
			}
			value, err := strconv.ParseInt(keyValue[1], 10, 64)
			if err != nil {
				continue
			}
			switch keyValue[0] {
			case "rbytes":
				readBytes += value
				found = true
			case "wbytes":
				writeBytes += value
				found = true
			}
		}
	}
	return readBytes, writeBytes, found
}

func readCgroupV1IO(path string) (int64, int64, bool) {
	payload, err := os.ReadFile(path)
	if err != nil {
		return 0, 0, false
	}
	var readBytes int64
	var writeBytes int64
	var found bool
	for _, line := range strings.Split(string(payload), "\n") {
		fields := strings.Fields(line)
		if len(fields) != 3 {
			continue
		}
		value, err := strconv.ParseInt(fields[2], 10, 64)
		if err != nil {
			continue
		}
		switch fields[1] {
		case "Read":
			readBytes += value
			found = true
		case "Write":
			writeBytes += value
			found = true
		}
	}
	return readBytes, writeBytes, found
}

func float64Pointer(value float64) *float64 {
	return &value
}

func int64Pointer(value int64) *int64 {
	return &value
}
