package process

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
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
	Status      Status                `json:"status"`
	Port        int                   `json:"port"`
	PID         int                   `json:"pid"`
	BaseURL     string                `json:"baseUrl"`
	SessionPath string                `json:"sessionPath"`
	ConfigPath  string                `json:"configPath"`
	Message     string                `json:"message"`
	TraceID     string                `json:"traceId"`
	Records     []state.ProcessRecord `json:"records,omitempty"`
}

// StartSpec 是 OSStarter 执行 opencode serve 所需的完整命令描述。
type StartSpec struct {
	Command     string
	Args        []string
	Env         map[string]string
	LogPath     string
	Port        int
	BaseURL     string
	SessionPath string
	ConfigPath  string
	TraceID     string
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
}

// NewManager 创建本地进程管理器；后续 socket 控制面可以复用该库。
func NewManager(cfg config.Config, store Store, starter Starter, signaler Signaler, checker health.Checker) *Manager {
	return &Manager{
		cfg:      cfg,
		store:    store,
		starter:  starter,
		signaler: signaler,
		checker:  checker,
	}
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
	return StartSpec{
		Command:     cfg.OpencodeBin,
		Args:        args,
		Env:         map[string]string{"XDG_DATA_HOME": sessionPath, "OPENCODE_CONFIG_DIR": cfg.ConfigDir},
		LogPath:     cfg.LogPath(request.Port),
		Port:        request.Port,
		BaseURL:     fmt.Sprintf("http://%s:%d", cfg.LinuxServerID, request.Port),
		SessionPath: sessionPath,
		ConfigPath:  cfg.ConfigDir,
		TraceID:     request.TraceID,
	}, nil
}

// Start 启动一个端口对应的 opencode server，并写入本地 state。
func (m *Manager) Start(ctx context.Context, request StartRequest) (Result, error) {
	spec, err := BuildStartSpec(m.cfg, request)
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
	if len(records) >= m.cfg.MaxProcesses {
		err := fmt.Errorf("container max processes reached")
		return failed(request.Port, request.TraceID, err), err
	}
	if err := os.MkdirAll(spec.SessionPath, 0o755); err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	if err := os.MkdirAll(spec.ConfigPath, 0o755); err != nil {
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
		Port:        request.Port,
		PID:         pid,
		BaseURL:     spec.BaseURL,
		SessionPath: spec.SessionPath,
		ConfigPath:  spec.ConfigPath,
		StartedAt:   time.Now().UTC(),
		TraceID:     request.TraceID,
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
	if err := m.signaler.Terminate(record.PID); err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	timeout := request.Timeout
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	if !m.waitStopped(ctx, record.PID, timeout) {
		if err := m.signaler.Kill(record.PID); err != nil {
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
	records, err := m.store.List()
	if err != nil {
		return failed(0, traceID, err), err
	}
	return Result{Status: StatusHealthy, Message: "listed managed processes", TraceID: traceID, Records: records}, nil
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

func result(status Status, record state.ProcessRecord, message string, traceID string) Result {
	return Result{
		Status:      status,
		Port:        record.Port,
		PID:         record.PID,
		BaseURL:     record.BaseURL,
		SessionPath: record.SessionPath,
		ConfigPath:  record.ConfigPath,
		Message:     message,
		TraceID:     traceID,
	}
}

func failed(port int, traceID string, err error) Result {
	message := ""
	if err != nil {
		message = err.Error()
	}
	return Result{Status: StatusFailed, Port: port, Message: message, TraceID: traceID}
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
