package process

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/icbc/test-agent/opencode-manager/internal/config"
	"github.com/icbc/test-agent/opencode-manager/internal/health"
	"github.com/icbc/test-agent/opencode-manager/internal/state"
)

// Status 是 opencode-manager CLI 输出的稳定状态枚举。
type Status string

const (
	StatusStarted   Status = "STARTED"
	StatusStopped   Status = "STOPPED"
	StatusHealthy   Status = "HEALTHY"
	StatusUnhealthy Status = "UNHEALTHY"
	StatusFailed    Status = "FAILED"
)

const (
	ErrorCodeOpencodeUnavailable = "OPENCODE_UNAVAILABLE"
)

var errPublicConfigNotInitialized = errors.New("public config directory not initialized")

// StartRequest 描述一次启动 opencode server 的本地命令。
type StartRequest struct {
	Port    int
	TraceID string
}

// StopRequest 描述一次停止命令，Timeout 控制 SIGTERM 后等待多久再强杀。
type StopRequest struct {
	Port    int
	TraceID string
	Timeout time.Duration
}

// HealthRequest 描述一次健康检测命令。
type HealthRequest struct {
	Port    int
	TraceID string
}

// Result 是所有进程操作的稳定 JSON 输出模型。
type Result struct {
	Status       Status                `json:"status"`
	Port         int                   `json:"port"`
	PID          int                   `json:"pid"`
	BaseURL      string                `json:"baseUrl"`
	SessionPath  string                `json:"sessionPath"`
	ConfigPath   string                `json:"configPath"`
	StartCommand string                `json:"startCommand,omitempty"`
	Message      string                `json:"message"`
	ErrorCode    string                `json:"errorCode,omitempty"`
	TraceID      string                `json:"traceId"`
	Records      []state.ProcessRecord `json:"records,omitempty"`
}

// StartSpec 是 OSStarter 执行 opencode serve 所需的完整命令描述。
type StartSpec struct {
	Command      string
	Args         []string
	Env          map[string]string
	LogPath      string
	Port         int
	BaseURL      string
	SessionPath  string
	ConfigPath   string
	StartCommand string
	TraceID      string
}

// Starter 隔离真实 os/exec，便于单测验证启动命令而不拉起真实 opencode。
type Starter interface {
	Start(ctx context.Context, spec StartSpec) (int, error)
}

// Signaler 隔离系统信号，便于测试 stop/restart 的进程清理语义。
type Signaler interface {
	Terminate(pid int) error
	Kill(pid int) error
}

// Store 是本地进程状态仓库所需的最小接口。
type Store interface {
	Create(record state.ProcessRecord) error
	Save(record state.ProcessRecord) error
	Get(port int) (state.ProcessRecord, bool, error)
	List() ([]state.ProcessRecord, error)
	Delete(port int) error
}

// Manager 组合配置、状态仓库和系统进程操作，提供本地生命周期能力。
type Manager struct {
	cfg      config.Config
	store    Store
	starter  Starter
	signaler Signaler
	checker  health.Checker

	cfgMu              sync.RWMutex
	runtimeConfigReady bool

	// maxProcesses 是运行时最大并发进程数，可由后端通过 configUpdate 热更新；
	// 初始值取自 cfg.MaxProcesses（env 兜底），后端下发值覆盖。
	maxProcesses atomic.Int64
}

// NewManager 创建本地进程管理器；后续 socket 控制面可以复用该库。
func NewManager(cfg config.Config, store Store, starter Starter, signaler Signaler, checker health.Checker) *Manager {
	m := &Manager{
		cfg:      cfg,
		store:    store,
		starter:  starter,
		signaler: signaler,
		checker:  checker,
	}
	m.maxProcesses.Store(int64(cfg.MaxProcesses))
	m.runtimeConfigReady = !cfg.RuntimeConfigRequired
	return m
}

// MaxProcesses 返回当前生效的最大并发进程数，供 topologyMessage 上报后端。
func (m *Manager) MaxProcesses() int {
	return int(m.maxProcesses.Load())
}

// SetMaxProcesses 应用后端下发的运行时最大进程数。
// v < 1 视为非法不予应用；v 超过本容器端口池容量时 clamp 到容量上限，
// 保证 maxProcesses 恒满足 1..(PortEnd-PortStart+1) 的不变量。
// 返回最终生效值与应用错误（非法值时 err 非 nil 且不变更当前值）。
func (m *Manager) SetMaxProcesses(v int) (int, error) {
	if v < 1 {
		return m.MaxProcesses(), fmt.Errorf("maxProcesses must be >= 1, got %d", v)
	}
	availablePorts := m.cfg.PortEnd - m.cfg.PortStart + 1
	applied := v
	if applied > availablePorts {
		applied = availablePorts
	}
	m.maxProcesses.Store(int64(applied))
	return applied, nil
}

// ApplyRuntimeConfig 应用 Java 公共参数下发的 manager 运行配置。
// sessionRoot/configDir 是启动 opencode server 的权威路径；maxProcesses 会按端口池容量裁剪。
func (m *Manager) ApplyRuntimeConfig(maxProcesses int, sessionRoot, configDir string) (int, error) {
	sessionRoot = strings.TrimSpace(sessionRoot)
	configDir = strings.TrimSpace(configDir)
	if sessionRoot == "" {
		return m.MaxProcesses(), fmt.Errorf("sessionRoot must not be blank")
	}
	if configDir == "" {
		return m.MaxProcesses(), fmt.Errorf("configDir must not be blank")
	}
	applied, err := m.SetMaxProcesses(maxProcesses)
	if err != nil {
		return applied, err
	}
	m.cfgMu.Lock()
	defer m.cfgMu.Unlock()
	m.cfg.SessionRoot = sessionRoot
	m.cfg.ConfigDir = configDir
	m.runtimeConfigReady = true
	return applied, nil
}

func (m *Manager) startConfig() (config.Config, error) {
	m.cfgMu.RLock()
	defer m.cfgMu.RUnlock()
	if m.cfg.RuntimeConfigRequired && !m.runtimeConfigReady {
		return config.Config{}, fmt.Errorf("manager runtime config not ready")
	}
	cfg := m.cfg
	cfg.MaxProcesses = m.MaxProcesses()
	cfg.RuntimeConfigRequired = false
	return cfg, nil
}

// BuildStartSpec 构造固定的 opencode serve 命令和启动环境。
func BuildStartSpec(cfg config.Config, request StartRequest) (StartSpec, error) {
	if err := cfg.Validate(); err != nil {
		return StartSpec{}, err
	}
	if err := cfg.ValidatePort(request.Port); err != nil {
		return StartSpec{}, err
	}
	if request.TraceID == "" {
		return StartSpec{}, fmt.Errorf("traceId is required")
	}

	sessionPath := cfg.SessionPath(request.Port)
	args := []string{"serve", "--hostname", "0.0.0.0", "--port", strconv.Itoa(request.Port), "--print-logs"}
	for _, origin := range cfg.AllowedCORS {
		args = append(args, "--cors", origin)
	}
	env := map[string]string{"XDG_DATA_HOME": sessionPath, "OPENCODE_CONFIG_DIR": cfg.ConfigDir}
	return StartSpec{
		Command:      cfg.OpencodeBin,
		Args:         args,
		Env:          env,
		LogPath:      cfg.LogPath(request.Port),
		Port:         request.Port,
		BaseURL:      fmt.Sprintf("http://%s:%d", cfg.LinuxServerID, request.Port),
		SessionPath:  sessionPath,
		ConfigPath:   cfg.ConfigDir,
		StartCommand: formatStartCommand(cfg.OpencodeBin, args, env),
		TraceID:      request.TraceID,
	}, nil
}

// Start 启动一个端口对应的 opencode server，并写入本地 state。
func (m *Manager) Start(ctx context.Context, request StartRequest) (Result, error) {
	cfg, err := m.startConfig()
	if err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	spec, err := BuildStartSpec(cfg, request)
	if err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	if record, ok, err := m.store.Get(request.Port); err != nil {
		return failed(request.Port, request.TraceID, err), err
	} else if ok {
		record.TraceID = request.TraceID
		// start 命令需要对健康的既有 state 幂等，便于后端在数据库记录丢失时补齐平台进程快照。
		checked := m.checker.Check(ctx, record)
		if checked.Status == health.StatusHealthy {
			return result(StatusStarted, record, "opencode server already managed and healthy", request.TraceID), nil
		}
		err := fmt.Errorf("port %d is already managed but unhealthy: %s", request.Port, checked.Message)
		return failed(request.Port, request.TraceID, err), err
	}
	records, err := m.store.List()
	if err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	if len(records) >= m.MaxProcesses() {
		err := fmt.Errorf("container max processes reached")
		return failed(request.Port, request.TraceID, err), err
	}
	if err := ensurePublicConfigInitialized(spec.ConfigPath); err != nil {
		// 公共配置目录（OPENCODE_PUBLIC_CONFIG_DIR）必须由管理员预先初始化，
		// manager 不自动创建，避免拉起一个没有公共 agent/provider 配置的空壳进程。
		return failedWithCode(
				request.Port,
				request.TraceID,
				ErrorCodeOpencodeUnavailable,
				publicConfigNotInitializedMessage(cfg.LinuxServerID, spec.ConfigPath)),
			err
	}
	if err := os.MkdirAll(spec.SessionPath, 0o755); err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	if err := os.MkdirAll(filepath.Dir(spec.LogPath), 0o755); err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	pid, err := m.starter.Start(ctx, spec)
	if err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	record := state.ProcessRecord{
		Port:         request.Port,
		PID:          pid,
		BaseURL:      spec.BaseURL,
		SessionPath:  spec.SessionPath,
		ConfigPath:   spec.ConfigPath,
		StartedAt:    time.Now().UTC(),
		StartCommand: spec.StartCommand,
		TraceID:      request.TraceID,
	}
	if err := m.store.Create(record); err != nil {
		_ = m.signaler.Terminate(pid)
		return failed(request.Port, request.TraceID, err), err
	}
	return result(StatusStarted, record, "opencode server started", request.TraceID), nil
}

// Stop 停止一个已记录端口的 opencode server，并清理本地 state。
func (m *Manager) Stop(ctx context.Context, request StopRequest) (Result, error) {
	record, ok, err := m.store.Get(request.Port)
	if err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	if !ok {
		err := fmt.Errorf("port %d is not managed", request.Port)
		return failed(request.Port, request.TraceID, err), err
	}
	finished := false
	if err := m.signaler.Terminate(record.PID); err != nil {
		if !isProcessFinishedError(err) {
			return failed(request.Port, request.TraceID, err), err
		}
		finished = true
	}
	timeout := request.Timeout
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	if !finished && !m.waitStopped(ctx, record.PID, timeout) {
		if err := m.signaler.Kill(record.PID); err != nil && !isProcessFinishedError(err) {
			return failed(request.Port, request.TraceID, err), err
		}
	}
	if err := m.store.Delete(request.Port); err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	record.TraceID = request.TraceID
	return result(StatusStopped, record, "opencode server stopped", request.TraceID), nil
}

// Restart 先停止旧进程，再使用同一端口启动新进程。
func (m *Manager) Restart(ctx context.Context, request StopRequest) (Result, error) {
	if _, err := m.Stop(ctx, request); err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	return m.Start(ctx, StartRequest{Port: request.Port, TraceID: request.TraceID})
}

// Health 查询本地 state 后执行 PID 和 HTTP 健康检测。
func (m *Manager) Health(ctx context.Context, request HealthRequest) (Result, error) {
	record, ok, err := m.store.Get(request.Port)
	if err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	if !ok {
		err := fmt.Errorf("port %d is not managed", request.Port)
		return failed(request.Port, request.TraceID, err), err
	}
	record.TraceID = request.TraceID
	checked := m.checker.Check(ctx, record)
	status := StatusUnhealthy
	if checked.Status == health.StatusHealthy {
		status = StatusHealthy
	}
	return result(status, record, checked.Message, request.TraceID), nil
}

// List 返回当前本地 state 中记录的全部 opencode server 进程。
func (m *Manager) List(traceID string) (Result, error) {
	records, err := m.activeRecords()
	if err != nil {
		return failed(0, traceID, err), err
	}
	records = m.withDerivedStartCommands(records, traceID)
	return Result{Status: StatusHealthy, Message: "listed managed processes", TraceID: traceID, Records: records}, nil
}

// activeRecords 在对外展示/心跳前清理已退出 PID 的陈旧 state，避免死进程继续占用容量。
func (m *Manager) activeRecords() ([]state.ProcessRecord, error) {
	records, err := m.store.List()
	if err != nil {
		return nil, err
	}
	alive := m.checker.ProcessAlive
	if alive == nil {
		alive = health.DefaultProcessAlive
	}
	active := records[:0]
	for _, record := range records {
		if !alive(record.PID) {
			if err := m.store.Delete(record.Port); err != nil {
				return nil, err
			}
			continue
		}
		active = append(active, record)
	}
	return active, nil
}

func (m *Manager) withDerivedStartCommands(records []state.ProcessRecord, traceID string) []state.ProcessRecord {
	cfg, err := m.startConfig()
	if err != nil {
		return records
	}
	for i := range records {
		if records[i].StartCommand != "" {
			continue
		}
		spec, err := BuildStartSpec(cfg, StartRequest{Port: records[i].Port, TraceID: nonEmptyTraceID(traceID)})
		if err == nil {
			records[i].StartCommand = spec.StartCommand
		}
	}
	return records
}

func isProcessFinishedError(err error) bool {
	if err == nil {
		return false
	}
	if errors.Is(err, os.ErrProcessDone) || errors.Is(err, syscall.ESRCH) {
		return true
	}
	message := strings.ToLower(err.Error())
	return strings.Contains(message, "process already finished") || strings.Contains(message, "no such process")
}

func nonEmptyTraceID(traceID string) string {
	if traceID != "" {
		return traceID
	}
	return "trace_list"
}

func (m *Manager) waitStopped(ctx context.Context, pid int, timeout time.Duration) bool {
	alive := m.checker.ProcessAlive
	if alive == nil {
		alive = health.DefaultProcessAlive
	}
	deadline := time.NewTimer(timeout)
	defer deadline.Stop()
	ticker := time.NewTicker(50 * time.Millisecond)
	defer ticker.Stop()
	for {
		if !alive(pid) {
			return true
		}
		select {
		case <-ctx.Done():
			return false
		case <-deadline.C:
			return false
		case <-ticker.C:
		}
	}
}

func ensurePublicConfigInitialized(path string) error {
	info, err := os.Stat(path)
	if err != nil {
		return errPublicConfigNotInitialized
	}
	if !info.IsDir() {
		return errPublicConfigNotInitialized
	}
	entries, err := os.ReadDir(path)
	if err != nil {
		return errPublicConfigNotInitialized
	}
	if len(entries) == 0 {
		return errPublicConfigNotInitialized
	}
	return nil
}

// publicConfigNotInitializedMessage 明确暴露 manager 实际检查的服务器和目录，便于定位 Java 页面状态与目标进程文件系统不一致的问题。
func publicConfigNotInitializedMessage(linuxServerID string, configPath string) string {
	return fmt.Sprintf("服务器%s，公共 opencode 配置目录%s尚未初始化。请联系超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化后重试。",
		linuxServerID,
		configPath)
}

func result(status Status, record state.ProcessRecord, message string, traceID string) Result {
	return Result{
		Status:       status,
		Port:         record.Port,
		PID:          record.PID,
		BaseURL:      record.BaseURL,
		SessionPath:  record.SessionPath,
		ConfigPath:   record.ConfigPath,
		StartCommand: record.StartCommand,
		Message:      message,
		TraceID:      traceID,
	}
}

func failed(port int, traceID string, err error) Result {
	message := ""
	if err != nil {
		message = err.Error()
	}
	return Result{Status: StatusFailed, Port: port, Message: message, TraceID: traceID}
}

func failedWithCode(port int, traceID string, errorCode string, message string) Result {
	return Result{Status: StatusFailed, Port: port, Message: message, ErrorCode: errorCode, TraceID: traceID}
}

// OSStarter 使用 os/exec 启动真实 opencode serve 进程。
type OSStarter struct{}

func (OSStarter) Start(_ context.Context, spec StartSpec) (int, error) {
	logFile, err := os.OpenFile(spec.LogPath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	if err != nil {
		return 0, err
	}
	defer logFile.Close()

	command := exec.Command(spec.Command, spec.Args...)
	command.Env = append(os.Environ(), flattenEnv(spec.Env)...)
	command.Stdout = logFile
	command.Stderr = logFile
	command.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	if err := command.Start(); err != nil {
		return 0, err
	}
	go func() {
		_ = command.Wait()
	}()
	return command.Process.Pid, nil
}

// OSSignaler 向真实 Unix 进程发送终止或强杀信号。
type OSSignaler struct{}

func (OSSignaler) Terminate(pid int) error {
	return signal(pid, syscall.SIGTERM)
}

func (OSSignaler) Kill(pid int) error {
	return signal(pid, syscall.SIGKILL)
}

func signal(pid int, sig syscall.Signal) error {
	// 启动时已设置独立进程组，优先按组发送信号以清理可能的子进程。
	if err := syscall.Kill(-pid, sig); err == nil {
		return nil
	}
	process, err := os.FindProcess(pid)
	if err != nil {
		return err
	}
	return process.Signal(sig)
}

func flattenEnv(values map[string]string) []string {
	env := make([]string, 0, len(values))
	for key, value := range values {
		env = append(env, key+"="+value)
	}
	return env
}

func formatStartCommand(command string, args []string, env map[string]string) string {
	parts := make([]string, 0, len(args)+3)
	for _, key := range []string{"XDG_DATA_HOME", "OPENCODE_CONFIG_DIR"} {
		if value, ok := env[key]; ok {
			parts = append(parts, key+"="+shellQuote(value))
		}
	}
	parts = append(parts, shellQuote(command))
	for _, arg := range args {
		parts = append(parts, shellQuote(arg))
	}
	return strings.Join(parts, " ")
}

func shellQuote(value string) string {
	if value == "" {
		return "''"
	}
	for _, char := range value {
		if !isShellSafe(char) {
			return "'" + strings.ReplaceAll(value, "'", "'\\''") + "'"
		}
	}
	return value
}

func isShellSafe(char rune) bool {
	return char >= 'A' && char <= 'Z' ||
		char >= 'a' && char <= 'z' ||
		char >= '0' && char <= '9' ||
		strings.ContainsRune("_./:=@%+-", char)
}
