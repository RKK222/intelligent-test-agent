package process

import (
	"context"
	"errors"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/enterprise/test-agent/opencode-manager/internal/config"
	"github.com/enterprise/test-agent/opencode-manager/internal/health"
	"github.com/enterprise/test-agent/opencode-manager/internal/state"
)

type OSStarter struct{}

type OSSignaler struct{}

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
	ErrorCodeOpencodeUnavailable      = "OPENCODE_UNAVAILABLE"
	ErrorCodePortConflict             = "PORT_CONFLICT"
	ErrorCodePortOutOfRange           = "PORT_OUT_OF_RANGE"
	ErrorCodeIdentityAlreadyManaged   = "IDENTITY_ALREADY_MANAGED"
	ErrorCodeIdentityConfigMismatch   = "IDENTITY_CONFIG_MISMATCH"
	ErrorCodeProcessNotManaged        = "PROCESS_NOT_MANAGED"
	ErrorCodeProcessOwnershipMismatch = "PROCESS_OWNERSHIP_MISMATCH"
)

var errPublicConfigNotInitialized = errors.New("public config directory not initialized")

// StartRequest 描述一次启动 opencode server 的本地命令。
type StartRequest struct {
	Port            int
	UnifiedAuthID   string
	SessionPath     string
	ConfigPath      string
	Environment     map[string]string
	BindingRecovery bool
	TraceID         string
}

// StopRequest 描述一次停止命令，Timeout 控制 SIGTERM 后等待多久再强杀。
type StopRequest struct {
	Port    int
	TraceID string
	Timeout time.Duration
}

// OwnedStopRequest 描述一次带实例所有权栅栏的停止命令。
// manager 必须在发信号前同时核验用户身份和 PID，禁止迟到命令误杀复用端口的新实例。
type OwnedStopRequest struct {
	Port                  int
	ExpectedUnifiedAuthID string
	ExpectedPID           int
	TraceID               string
	Timeout               time.Duration
}

// HealthRequest 描述一次健康检测命令。
type HealthRequest struct {
	Port    int
	TraceID string
}

// Result 是所有进程操作的稳定 JSON 输出模型。
type Result struct {
	Status         Status                `json:"status"`
	Port           int                   `json:"port"`
	PID            int                   `json:"pid"`
	BaseURL        string                `json:"baseUrl"`
	SessionPath    string                `json:"sessionPath"`
	ConfigPath     string                `json:"configPath"`
	StartCommand   string                `json:"startCommand,omitempty"`
	ProcessCreated bool                  `json:"processCreated"`
	Message        string                `json:"message"`
	ErrorCode      string                `json:"errorCode,omitempty"`
	TraceID        string                `json:"traceId"`
	Records        []state.ProcessRecord `json:"records,omitempty"`
}

// StartSpec 是 OSStarter 执行 opencode serve 所需的完整命令描述。
type StartSpec struct {
	Command       string
	Args          []string
	Env           map[string]string
	LogPath       string
	UnifiedAuthID string
	StartedAt     time.Time
	Port          int
	BaseURL       string
	SessionPath   string
	ConfigPath    string
	StartCommand  string
	TraceID       string
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

	// lifecycleMu 串行化同一 manager 内的 start、stop、restart，
	// 保证状态检查、端口探测和进程变更不会被并发 WebSocket 命令穿插。
	lifecycleMu sync.Mutex
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
// 首次完整帧必须同时携带 sessionRoot/configDir；后续 max-only 帧用空路径表示保持原路径不变。
// maxProcesses 会按端口池容量裁剪。
func (m *Manager) ApplyRuntimeConfig(maxProcesses int, sessionRoot, configDir string) (int, error) {
	sessionRoot = strings.TrimSpace(sessionRoot)
	configDir = strings.TrimSpace(configDir)
	if sessionRoot == "" && configDir != "" {
		return m.MaxProcesses(), fmt.Errorf("sessionRoot must not be blank")
	}
	if sessionRoot != "" && configDir == "" {
		return m.MaxProcesses(), fmt.Errorf("configDir must not be blank")
	}
	applied, err := m.SetMaxProcesses(maxProcesses)
	if err != nil {
		return applied, err
	}
	if sessionRoot == "" && configDir == "" {
		return applied, nil
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
	return buildStartSpec(cfg, request, time.Now().UTC())
}

func buildStartSpec(cfg config.Config, request StartRequest, startedAt time.Time) (StartSpec, error) {
	if err := cfg.Validate(); err != nil {
		return StartSpec{}, err
	}
	if err := cfg.ValidatePort(request.Port); err != nil {
		return StartSpec{}, err
	}
	if request.TraceID == "" {
		return StartSpec{}, fmt.Errorf("traceId is required")
	}
	startedAt = startedAt.UTC()
	if startedAt.IsZero() {
		return StartSpec{}, fmt.Errorf("startedAt is required")
	}

	sessionPath := strings.TrimSpace(request.SessionPath)
	if sessionPath == "" {
		sessionPath = cfg.SessionPath(request.Port)
	} else {
		// Java 侧按用户生成稳定目录；manager 只规整路径并保留旧端口目录 fallback。
		sessionPath = filepath.Clean(sessionPath)
	}
	unifiedAuthID, err := resolveUnifiedAuthID(request.UnifiedAuthID, sessionPath)
	if err != nil {
		return StartSpec{}, err
	}
	args := []string{"serve", "--hostname", "0.0.0.0", "--port", strconv.Itoa(request.Port), "--print-logs"}
	for _, origin := range cfg.AllowedCORS {
		args = append(args, "--cors", origin)
	}
	configPath := strings.TrimSpace(request.ConfigPath)
	if configPath == "" {
		// 旧 CLI 与滚动升级期间未携带个人路径的命令继续读取公共共享配置。
		configPath = cfg.ConfigDir
	} else {
		configPath = filepath.Clean(configPath)
	}
	env := make(map[string]string, len(request.Environment)+6)
	for key, value := range request.Environment {
		if strings.TrimSpace(key) != "" {
			env[key] = value
		}
	}
	// 用户运行目录全部由 manager 基于已校验的 sessionPath 派生，调用方环境不得覆盖隔离边界。
	env["HOME"] = sessionPath
	env["XDG_DATA_HOME"] = sessionPath
	env["XDG_CACHE_HOME"] = filepath.Join(sessionPath, ".cache")
	env["XDG_STATE_HOME"] = filepath.Join(sessionPath, ".local", "state")
	env["TMPDIR"] = filepath.Join(sessionPath, ".tmp")
	env["OPENCODE_CONFIG_DIR"] = configPath
	return StartSpec{
		Command:       cfg.OpencodeBin,
		Args:          args,
		Env:           env,
		LogPath:       processLogPath(cfg, unifiedAuthID, startedAt, request.Port),
		UnifiedAuthID: unifiedAuthID,
		StartedAt:     startedAt,
		Port:          request.Port,
		BaseURL:       fmt.Sprintf("http://%s:%d", cfg.ServerHost, request.Port),
		SessionPath:   sessionPath,
		ConfigPath:    configPath,
		StartCommand:  formatStartCommand(cfg.OpencodeBin, args, env),
		TraceID:       request.TraceID,
	}, nil
}

// Start 启动一个端口对应的 opencode server，并写入本地 state。
func (m *Manager) Start(ctx context.Context, request StartRequest) (Result, error) {
	m.lifecycleMu.Lock()
	defer m.lifecycleMu.Unlock()
	return m.start(ctx, request)
}

// start 在 lifecycleMu 已持有时执行实际启动流程。
func (m *Manager) start(ctx context.Context, request StartRequest) (Result, error) {
	cfg, err := m.startConfig()
	if err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	if err := cfg.ValidatePort(request.Port); err != nil {
		return failedWithCode(request.Port, request.TraceID, ErrorCodePortOutOfRange, err.Error()), err
	}
	spec, err := buildStartSpec(cfg, request, time.Now().UTC())
	if err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	if record, ok, err := m.store.Get(request.Port); err != nil {
		return failed(request.Port, request.TraceID, err), err
	} else if ok {
		record.TraceID = request.TraceID
		if recordUnifiedAuthID(record) != strings.TrimSpace(spec.UnifiedAuthID) {
			err := fmt.Errorf("requested port is already managed by another identity")
			return failedWithCode(request.Port, request.TraceID, ErrorCodePortConflict, err.Error()), err
		}
		// 同一用户也只能复用同一会话与配置身份路径，防止把旧运行时误报为新配置。
		if !sameManagedIdentityPaths(record, spec) {
			err := fmt.Errorf("managed process session or config identity does not match the request")
			return failedWithCode(request.Port, request.TraceID, ErrorCodeIdentityConfigMismatch, err.Error()), err
		}
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
	if unifiedAuthID := strings.TrimSpace(spec.UnifiedAuthID); unifiedAuthID != "" {
		for _, record := range records {
			if record.Port != request.Port && recordUnifiedAuthID(record) == unifiedAuthID {
				err := fmt.Errorf("requested identity is already managed at another port")
				return failedWithCode(request.Port, request.TraceID, ErrorCodeIdentityAlreadyManaged, err.Error()), err
			}
		}
	}
	// 已有平台 binding 的精确原端口恢复不属于新调度；它仍需通过身份、端口和监听冲突校验。
	if len(records) >= m.MaxProcesses() && !request.BindingRecovery {
		err := fmt.Errorf("container max processes reached")
		return failed(request.Port, request.TraceID, err), err
	}
	if err := ensurePortAvailable(request.Port); err != nil {
		return failedWithCode(request.Port, request.TraceID, ErrorCodePortConflict, "requested port is already in use"), err
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
	if err := ensureUserRuntimeDirectories(spec); err != nil {
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
		Port:          request.Port,
		PID:           pid,
		BaseURL:       spec.BaseURL,
		UnifiedAuthID: spec.UnifiedAuthID,
		SessionPath:   spec.SessionPath,
		ConfigPath:    spec.ConfigPath,
		StartedAt:     spec.StartedAt,
		StartCommand:  spec.StartCommand,
		TraceID:       request.TraceID,
	}
	if err := m.store.Create(record); err != nil {
		_ = m.signaler.Terminate(pid)
		return failed(request.Port, request.TraceID, err), err
	}
	started := result(StatusStarted, record, "opencode server started", request.TraceID)
	started.ProcessCreated = true
	return started, nil
}

// ensurePortAvailable 按子进程实际绑定范围探测端口，覆盖通配地址和 loopback 专属监听。
// 调用方在 lifecycleMu 内执行，确保本 manager 的检查与子进程启动不会彼此竞争。
func ensurePortAvailable(port int) error {
	for _, host := range []string{"0.0.0.0", "127.0.0.1"} {
		listener, err := net.Listen("tcp4", net.JoinHostPort(host, strconv.Itoa(port)))
		if err != nil {
			return err
		}
		if err := listener.Close(); err != nil {
			return err
		}
	}
	for _, host := range localIPv4Hosts() {
		connection, err := net.DialTimeout("tcp4", net.JoinHostPort(host, strconv.Itoa(port)), 100*time.Millisecond)
		if err != nil {
			continue
		}
		_ = connection.Close()
		return fmt.Errorf("port %d has an active TCP listener", port)
	}
	return nil
}

// localIPv4Hosts 返回本机可用 IPv4 地址，用于补足部分平台上通配绑定不报告特定地址冲突的差异。
func localIPv4Hosts() []string {
	hosts := []string{"127.0.0.1"}
	interfaces, err := net.Interfaces()
	if err != nil {
		return hosts
	}
	seen := map[string]bool{"127.0.0.1": true}
	for _, iface := range interfaces {
		if iface.Flags&net.FlagUp == 0 {
			continue
		}
		addresses, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, address := range addresses {
			ipNet, ok := address.(*net.IPNet)
			if !ok {
				continue
			}
			ipv4 := ipNet.IP.To4()
			if ipv4 == nil {
				continue
			}
			host := ipv4.String()
			if !seen[host] {
				seen[host] = true
				hosts = append(hosts, host)
			}
		}
	}
	return hosts
}

func sameManagedIdentityPaths(record state.ProcessRecord, spec StartSpec) bool {
	return cleanIdentityPath(record.SessionPath) == cleanIdentityPath(spec.SessionPath) &&
		cleanIdentityPath(record.ConfigPath) == cleanIdentityPath(spec.ConfigPath)
}

func cleanIdentityPath(path string) string {
	return filepath.Clean(strings.TrimSpace(path))
}

// recordUnifiedAuthID 兼容滚动升级前缺少 unifiedAuthId 的本地 state，
// 优先使用已持久化身份，否则仅从稳定 users/{id} 会话路径恢复。
func recordUnifiedAuthID(record state.ProcessRecord) string {
	if unifiedAuthID := strings.TrimSpace(record.UnifiedAuthID); unifiedAuthID != "" {
		return unifiedAuthID
	}
	return unifiedAuthIDFromSessionPath(record.SessionPath)
}

// Stop 停止一个已记录端口的 opencode server，并清理本地 state。
func (m *Manager) Stop(ctx context.Context, request StopRequest) (Result, error) {
	m.lifecycleMu.Lock()
	defer m.lifecycleMu.Unlock()
	return m.stop(ctx, request)
}

// StopOwned 在同一生命周期临界区内完成 state 读取、所有权核验和停止副作用。
func (m *Manager) StopOwned(ctx context.Context, request OwnedStopRequest) (Result, error) {
	m.lifecycleMu.Lock()
	defer m.lifecycleMu.Unlock()
	record, ok, err := m.store.Get(request.Port)
	if err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	if !ok {
		err := fmt.Errorf("port %d is not managed", request.Port)
		return failedWithCode(request.Port, request.TraceID, ErrorCodeProcessNotManaged, err.Error()), err
	}
	managedUnifiedAuthID := recordUnifiedAuthID(record)
	if managedUnifiedAuthID == "" ||
		strings.TrimSpace(request.ExpectedUnifiedAuthID) == "" ||
		managedUnifiedAuthID != strings.TrimSpace(request.ExpectedUnifiedAuthID) ||
		request.ExpectedPID < 1 ||
		record.PID != request.ExpectedPID {
		err := errors.New("managed process ownership does not match stop request")
		return failedWithCode(
			request.Port,
			request.TraceID,
			ErrorCodeProcessOwnershipMismatch,
			err.Error()), err
	}
	return m.stopRecord(ctx, StopRequest{
		Port: request.Port, TraceID: request.TraceID, Timeout: request.Timeout,
	}, record)
}

// stop 在 lifecycleMu 已持有时执行实际停止流程，供 restart 在同一临界区复用。
func (m *Manager) stop(ctx context.Context, request StopRequest) (Result, error) {
	record, ok, err := m.store.Get(request.Port)
	if err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	if !ok {
		err := fmt.Errorf("port %d is not managed", request.Port)
		return failed(request.Port, request.TraceID, err), err
	}
	return m.stopRecord(ctx, request, record)
}

// stopRecord 对已经在 lifecycleMu 内读取并按需校验过的精确 state 执行停止。
func (m *Manager) stopRecord(ctx context.Context, request StopRequest, record state.ProcessRecord) (Result, error) {
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
		if err := m.signaler.Kill(record.PID); err != nil {
			if !isProcessFinishedError(err) {
				return failed(request.Port, request.TraceID, err), err
			}
			finished = true
		}
		// 发送 SIGKILL 只代表信号已提交；必须再次确认 PID 消失后才能删除权威 state。
		if !finished && !m.waitStopped(ctx, record.PID, timeout) {
			err := fmt.Errorf("process did not exit after force kill")
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
	m.lifecycleMu.Lock()
	defer m.lifecycleMu.Unlock()
	return m.restart(ctx, request)
}

// restart 在 lifecycleMu 已持有时复用无锁辅助方法，避免 Restart 调用公开 Stop/Start 时自锁。
func (m *Manager) restart(ctx context.Context, request StopRequest) (Result, error) {
	record, ok, err := m.store.Get(request.Port)
	if err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	sessionPath := ""
	configPath := ""
	unifiedAuthID := ""
	if ok {
		sessionPath = record.SessionPath
		configPath = record.ConfigPath
		unifiedAuthID = record.UnifiedAuthID
	}
	if _, err := m.stop(ctx, request); err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	return m.start(ctx, StartRequest{
		Port: request.Port, UnifiedAuthID: unifiedAuthID,
		SessionPath: sessionPath, ConfigPath: configPath, TraceID: request.TraceID,
	})
}

// Health 查询本地 state 后执行 PID 和 HTTP 健康检测。
func (m *Manager) Health(ctx context.Context, request HealthRequest) (Result, error) {
	record, ok, err := m.store.Get(request.Port)
	if err != nil {
		return failed(request.Port, request.TraceID, err), err
	}
	if !ok {
		err := fmt.Errorf("port %d is not managed", request.Port)
		return failedWithCode(
			request.Port,
			request.TraceID,
			ErrorCodeProcessNotManaged,
			err.Error()), err
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
	active := make([]state.ProcessRecord, 0, len(records))
	for _, record := range records {
		current, keep, err := m.reconcileActiveRecord(record, alive)
		if err != nil {
			return nil, err
		}
		if keep {
			active = append(active, current)
		}
	}
	return active, nil
}

// reconcileActiveRecord 只删除仍与心跳快照 PID 一致的陈旧 state。
// restart 可能在首次 PID 探测后写入同端口的新 state，因此删除前必须进入生命周期临界区并重新读取。
func (m *Manager) reconcileActiveRecord(
	snapshot state.ProcessRecord,
	alive func(int) bool,
) (state.ProcessRecord, bool, error) {
	if alive(snapshot.PID) {
		return snapshot, true, nil
	}
	m.lifecycleMu.Lock()
	defer m.lifecycleMu.Unlock()
	current, ok, err := m.store.Get(snapshot.Port)
	if err != nil || !ok {
		return state.ProcessRecord{}, false, err
	}
	if current.PID != snapshot.PID {
		// 当前 state 已被生命周期命令替换；本次旧快照无权删除它。
		if alive(current.PID) {
			return current, true, nil
		}
		return state.ProcessRecord{}, false, nil
	}
	if alive(current.PID) {
		return current, true, nil
	}
	if err := m.store.Delete(current.Port); err != nil {
		return state.ProcessRecord{}, false, err
	}
	return state.ProcessRecord{}, false, nil
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
		spec, err := BuildStartSpec(cfg, StartRequest{
			Port:          records[i].Port,
			UnifiedAuthID: records[i].UnifiedAuthID,
			SessionPath:   records[i].SessionPath,
			ConfigPath:    records[i].ConfigPath,
			TraceID:       nonEmptyTraceID(traceID),
		})
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
	if errors.Is(err, os.ErrProcessDone) {
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

// ensureUserRuntimeDirectories 在 fork 前创建用户独占的 HOME、缓存、状态和临时目录。
// 这些目录必须是普通物理目录，禁止通过文件或软链接把不同统一认证号重新汇聚到共享位置。
func ensureUserRuntimeDirectories(spec StartSpec) error {
	statePath := spec.Env["XDG_STATE_HOME"]
	directories := []struct {
		kind string
		path string
	}{
		{kind: "home", path: spec.Env["HOME"]},
		{kind: "cache", path: spec.Env["XDG_CACHE_HOME"]},
		{kind: "state parent", path: filepath.Dir(statePath)},
		{kind: "state", path: statePath},
		{kind: "temporary", path: spec.Env["TMPDIR"]},
	}
	for _, directory := range directories {
		if err := ensurePhysicalDirectory(directory.path, 0o755); err != nil {
			// PathError 会携带原始统一认证号路径；控制面和生命周期日志只返回目录类别。
			return fmt.Errorf("prepare user %s directory failed", directory.kind)
		}
	}
	return nil
}

// ensurePhysicalDirectory 创建固定权限目录；已存在路径也必须保持为普通目录。
func ensurePhysicalDirectory(directory string, permission os.FileMode) error {
	directory = strings.TrimSpace(directory)
	if directory == "" {
		return fmt.Errorf("runtime directory must not be blank")
	}
	info, err := os.Lstat(directory)
	if err == nil {
		if info.Mode()&os.ModeSymlink != 0 || !info.IsDir() {
			return fmt.Errorf("runtime path is not a physical directory")
		}
		return os.Chmod(directory, permission)
	}
	if !errors.Is(err, os.ErrNotExist) {
		return err
	}
	if err := os.MkdirAll(directory, permission); err != nil {
		return err
	}
	info, err = os.Lstat(directory)
	if err != nil {
		return err
	}
	if info.Mode()&os.ModeSymlink != 0 || !info.IsDir() {
		return fmt.Errorf("runtime path is not a physical directory")
	}
	return os.Chmod(directory, permission)
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

func flattenEnv(values map[string]string) []string {
	env := make([]string, 0, len(values))
	for key, value := range values {
		env = append(env, key+"="+value)
	}
	return env
}

func formatStartCommand(command string, args []string, env map[string]string) string {
	parts := make([]string, 0, len(args)+3)
	for _, key := range []string{
		"HOME",
		"XDG_DATA_HOME",
		"XDG_CACHE_HOME",
		"XDG_STATE_HOME",
		"TMPDIR",
		"OPENCODE_CONFIG_DIR",
		"OPENCODE_REFERENCES_DIR",
		"TEST_AGENT_INTERNAL_PROXY_BASE_URL",
		"TEST_AGENT_INTERNAL_PROXY_API_KEY",
		"ENTERPRISE_UCID",
	} {
		if value, ok := env[key]; ok {
			if key == "TEST_AGENT_INTERNAL_PROXY_API_KEY" {
				value = "<redacted>"
			}
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
