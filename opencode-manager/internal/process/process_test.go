package process

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"syscall"
	"testing"
	"time"

	"github.com/icbc/test-agent/opencode-manager/internal/config"
	"github.com/icbc/test-agent/opencode-manager/internal/health"
	"github.com/icbc/test-agent/opencode-manager/internal/state"
)

func TestBuildStartSpecUsesFixedOpencodeServeCommand(t *testing.T) {
	cfg := testConfig(t)

	spec, err := BuildStartSpec(cfg, StartRequest{Port: 4096, TraceID: "trace_1234567890abcdef"})
	if err != nil {
		t.Fatalf("BuildStartSpec returned error: %v", err)
	}

	wantArgs := []string{"serve", "--hostname", "0.0.0.0", "--port", "4096", "--print-logs"}
	if spec.Command != "opencode" {
		t.Fatalf("unexpected command %q", spec.Command)
	}
	if !equalStrings(spec.Args, wantArgs) {
		t.Fatalf("unexpected args %#v", spec.Args)
	}
	if spec.Env["XDG_DATA_HOME"] != "/tmp/sessions/4096" {
		t.Fatalf("unexpected session env %#v", spec.Env)
	}
	if spec.Env["OPENCODE_CONFIG_DIR"] != "/tmp/config/opencode/" {
		t.Fatalf("unexpected config env %#v", spec.Env)
	}
	if spec.BaseURL != "http://10.8.0.12:4096" {
		t.Fatalf("unexpected base url %q", spec.BaseURL)
	}
	wantStartCommand := "XDG_DATA_HOME=/tmp/sessions/4096 OPENCODE_CONFIG_DIR=/tmp/config/opencode/ opencode serve --hostname 0.0.0.0 --port 4096 --print-logs"
	if spec.StartCommand != wantStartCommand {
		t.Fatalf("unexpected start command %q", spec.StartCommand)
	}
}

func TestManagerStartWritesStateAndReusesHealthyManagedPort(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	starter := &fakeStarter{pid: 12345}
	healthServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer healthServer.Close()
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{
		ProcessAlive: func(pid int) bool { return true },
		ProbeBaseURL: healthServer.URL,
	})

	result, err := manager.Start(context.Background(), StartRequest{Port: 4096, TraceID: "trace_1234567890abcdef"})
	if err != nil {
		t.Fatalf("Start returned error: %v", err)
	}
	if result.Status != StatusStarted || result.PID != 12345 {
		t.Fatalf("unexpected start result %#v", result)
	}
	if len(starter.specs) != 1 {
		t.Fatalf("expected one start spec, got %d", len(starter.specs))
	}
	record, ok, err := store.Get(4096)
	if err != nil || !ok {
		t.Fatalf("expected state record, ok=%v err=%v", ok, err)
	}
	if record.StartCommand != starter.specs[0].StartCommand {
		t.Fatalf("expected persisted start command %q, got %q", starter.specs[0].StartCommand, record.StartCommand)
	}
	reused, err := manager.Start(context.Background(), StartRequest{Port: 4096, TraceID: "trace_1234567890abcdef"})
	if err != nil {
		t.Fatalf("Start should reuse healthy managed port, got error: %v", err)
	}
	if reused.Status != StatusStarted || reused.PID != 12345 {
		t.Fatalf("unexpected reused result %#v", reused)
	}
	if len(starter.specs) != 1 {
		t.Fatalf("expected duplicate start to reuse existing process, got %d starts", len(starter.specs))
	}
}

func TestManagerStartRejectsWhenMaxProcessesReached(t *testing.T) {
	cfg := testConfig(t)
	cfg.MaxProcesses = 1
	store := state.NewFileStore(t.TempDir())
	_ = store.Save(state.ProcessRecord{Port: 4097, PID: 22345, StartedAt: time.Now().UTC(), TraceID: "trace_old"})
	manager := NewManager(cfg, store, &fakeStarter{pid: 12345}, fakeSignaler{}, health.Checker{})

	result, err := manager.Start(context.Background(), StartRequest{Port: 4096, TraceID: "trace_1234567890abcdef"})
	if err == nil {
		t.Fatalf("expected max processes error")
	}
	if result.Status != StatusFailed {
		t.Fatalf("expected failed result, got %#v", result)
	}
}

func TestManagerStartRejectsWhenPublicConfigDirMissing(t *testing.T) {
	// 公共配置目录未由管理员初始化时，manager 不得自动创建，应直接失败。
	cfg := testConfig(t)
	cfg.ConfigDir = t.TempDir() + "/never-created/.config/opencode/"

	result := assertPublicConfigRejected(t, cfg)
	if _, statErr := os.Stat(cfg.ConfigDir); !os.IsNotExist(statErr) {
		t.Fatalf("expected config dir to remain absent, statErr=%v", statErr)
	}
	if result.ConfigPath != "" {
		t.Fatalf("failed result must not expose config path, got %q", result.ConfigPath)
	}
}

func TestManagerStartRejectsWhenPublicConfigDirIsEmpty(t *testing.T) {
	cfg := testConfig(t)
	cfg.ConfigDir = filepath.Join(t.TempDir(), "empty-opencode-config")
	if err := os.MkdirAll(cfg.ConfigDir, 0o755); err != nil {
		t.Fatalf("pre-create empty config dir: %v", err)
	}

	assertPublicConfigRejected(t, cfg)
}

func TestManagerStartRejectsWhenPublicConfigPathIsFile(t *testing.T) {
	cfg := testConfig(t)
	cfg.ConfigDir = filepath.Join(t.TempDir(), "opencode-config-file")
	if err := os.WriteFile(cfg.ConfigDir, []byte("not a directory"), 0o644); err != nil {
		t.Fatalf("pre-create config file: %v", err)
	}

	assertPublicConfigRejected(t, cfg)
}

func TestManagerSetMaxProcessesAppliesWithinRange(t *testing.T) {
	manager := NewManager(testConfig(t), state.NewFileStore(t.TempDir()), &fakeStarter{pid: 12345}, fakeSignaler{}, health.Checker{})
	if manager.MaxProcesses() != 4 {
		t.Fatalf("expected initial max 4, got %d", manager.MaxProcesses())
	}
	applied, err := manager.SetMaxProcesses(3)
	if err != nil || applied != 3 {
		t.Fatalf("SetMaxProcesses(3) applied=%d err=%v", applied, err)
	}
	if manager.MaxProcesses() != 3 {
		t.Fatalf("expected runtime max 3, got %d", manager.MaxProcesses())
	}
}

func TestManagerSetMaxProcessesClampsToPortCapacity(t *testing.T) {
	// testConfig 端口池 4096..4100 共 5 个端口，下发 99 必须 clamp 到 5。
	manager := NewManager(testConfig(t), state.NewFileStore(t.TempDir()), &fakeStarter{pid: 12345}, fakeSignaler{}, health.Checker{})
	applied, err := manager.SetMaxProcesses(99)
	if err != nil || applied != 5 {
		t.Fatalf("SetMaxProcesses(99) applied=%d err=%v", applied, err)
	}
	if manager.MaxProcesses() != 5 {
		t.Fatalf("expected clamped max 5, got %d", manager.MaxProcesses())
	}
}

func TestManagerSetMaxProcessesRejectsBelowOne(t *testing.T) {
	manager := NewManager(testConfig(t), state.NewFileStore(t.TempDir()), &fakeStarter{pid: 12345}, fakeSignaler{}, health.Checker{})
	original := manager.MaxProcesses()
	applied, err := manager.SetMaxProcesses(0)
	if err == nil {
		t.Fatalf("expected error for maxProcesses=0")
	}
	if applied != original {
		t.Fatalf("expected unchanged value %d, got %d", original, applied)
	}
	if manager.MaxProcesses() != original {
		t.Fatalf("maxProcesses must not change on rejected update, got %d", manager.MaxProcesses())
	}
}

func TestManagerStartRespectsRuntimeMaxProcesses(t *testing.T) {
	// 初始 max=4，运行时下调到 1；已存在 1 条记录后，再 Start 必须因运行时上限失败。
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	_ = store.Save(state.ProcessRecord{Port: 4097, PID: 22345, StartedAt: time.Now().UTC(), TraceID: "trace_old"})
	manager := NewManager(cfg, store, &fakeStarter{pid: 12345}, fakeSignaler{}, health.Checker{})
	if _, err := manager.SetMaxProcesses(1); err != nil {
		t.Fatalf("SetMaxProcesses(1) failed: %v", err)
	}

	result, err := manager.Start(context.Background(), StartRequest{Port: 4096, TraceID: "trace_1234567890abcdef"})
	if err == nil {
		t.Fatalf("expected runtime max processes error")
	}
	if result.Status != StatusFailed {
		t.Fatalf("expected failed result, got %#v", result)
	}
}

func TestManagerStartRejectsBeforeRuntimeConfigReady(t *testing.T) {
	cfg := testConfig(t)
	cfg.RuntimeConfigRequired = true
	cfg.SessionRoot = ""
	cfg.ConfigDir = ""
	manager := NewManager(cfg, state.NewFileStore(t.TempDir()), &fakeStarter{pid: 12345}, fakeSignaler{}, health.Checker{})

	result, err := manager.Start(context.Background(), StartRequest{Port: 4096, TraceID: "trace_1234567890abcdef"})
	if err == nil {
		t.Fatalf("expected runtime config not ready error")
	}
	if result.Status != StatusFailed {
		t.Fatalf("expected failed result, got %#v", result)
	}
}

func TestManagerApplyRuntimeConfigUsesCommonParameterPaths(t *testing.T) {
	cfg := testConfig(t)
	cfg.RuntimeConfigRequired = true
	cfg.SessionRoot = ""
	cfg.ConfigDir = ""
	store := state.NewFileStore(t.TempDir())
	starter := &fakeStarter{pid: 12345}
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{})
	sessionRoot := t.TempDir() + "/.session/"
	configDir := t.TempDir() + "/.config/opencode/"
	initializePublicConfigDir(t, configDir)

	applied, err := manager.ApplyRuntimeConfig(99, sessionRoot, configDir)
	if err != nil {
		t.Fatalf("ApplyRuntimeConfig returned error: %v", err)
	}
	if applied != 5 {
		t.Fatalf("expected max processes to clamp to port capacity, got %d", applied)
	}
	result, err := manager.Start(context.Background(), StartRequest{Port: 4096, TraceID: "trace_1234567890abcdef"})
	if err != nil {
		t.Fatalf("Start returned error after runtime config: %v", err)
	}
	if result.SessionPath != sessionRoot+"4096" {
		t.Fatalf("expected session path from common parameter, got %q", result.SessionPath)
	}
	if result.ConfigPath != configDir {
		t.Fatalf("expected config path from common parameter, got %q", result.ConfigPath)
	}
	wantStartCommand := "XDG_DATA_HOME=" + sessionRoot + "4096 OPENCODE_CONFIG_DIR=" + configDir + " opencode serve --hostname 0.0.0.0 --port 4096 --print-logs"
	if starter.specs[0].StartCommand != wantStartCommand {
		t.Fatalf("unexpected start command %q", starter.specs[0].StartCommand)
	}
}

func TestManagerStopTerminatesProcessAndRemovesState(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	_ = store.Save(state.ProcessRecord{Port: 4096, PID: 12345, StartedAt: time.Now().UTC(), TraceID: "trace_old"})
	signaler := &recordingSignaler{}
	manager := NewManager(cfg, store, &fakeStarter{pid: 12345}, signaler, health.Checker{
		ProcessAlive: func(pid int) bool { return false },
	})

	result, err := manager.Stop(context.Background(), StopRequest{Port: 4096, TraceID: "trace_1234567890abcdef", Timeout: time.Millisecond})
	if err != nil {
		t.Fatalf("Stop returned error: %v", err)
	}
	if result.Status != StatusStopped {
		t.Fatalf("unexpected stop result %#v", result)
	}
	if !signaler.terminated {
		t.Fatalf("expected SIGTERM to be sent")
	}
	_, ok, err := store.Get(4096)
	if err != nil || ok {
		t.Fatalf("expected state to be removed, ok=%v err=%v", ok, err)
	}
}

func TestManagerStopTreatsFinishedProcessAsStoppedAndRemovesState(t *testing.T) {
	cases := []struct {
		name string
		err  error
	}{
		{name: "os ErrProcessDone", err: os.ErrProcessDone},
		{name: "syscall ESRCH", err: syscall.ESRCH},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			cfg := testConfig(t)
			store := state.NewFileStore(t.TempDir())
			_ = store.Save(state.ProcessRecord{Port: 4096, PID: 12345, StartedAt: time.Now().UTC(), TraceID: "trace_old"})
			manager := NewManager(cfg, store, &fakeStarter{pid: 12345}, &errorSignaler{terminateErr: tc.err}, health.Checker{
				ProcessAlive: func(pid int) bool { return false },
			})

			result, err := manager.Stop(context.Background(), StopRequest{Port: 4096, TraceID: "trace_1234567890abcdef", Timeout: time.Millisecond})
			if err != nil {
				t.Fatalf("Stop should treat finished process as stopped, got error: %v", err)
			}
			if result.Status != StatusStopped {
				t.Fatalf("unexpected stop result %#v", result)
			}
			_, ok, err := store.Get(4096)
			if err != nil || ok {
				t.Fatalf("expected stale state to be removed, ok=%v err=%v", ok, err)
			}
		})
	}
}

func TestManagerStopKillsProcessAfterTimeout(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	_ = store.Save(state.ProcessRecord{Port: 4096, PID: 12345, StartedAt: time.Now().UTC(), TraceID: "trace_old"})
	signaler := &recordingSignaler{}
	manager := NewManager(cfg, store, &fakeStarter{pid: 12345}, signaler, health.Checker{
		ProcessAlive: func(pid int) bool { return true },
	})

	_, err := manager.Stop(context.Background(), StopRequest{Port: 4096, TraceID: "trace_1234567890abcdef", Timeout: time.Millisecond})
	if err != nil {
		t.Fatalf("Stop returned error: %v", err)
	}
	if !signaler.terminated || !signaler.killed {
		t.Fatalf("expected SIGTERM and SIGKILL to be sent, got terminated=%v killed=%v", signaler.terminated, signaler.killed)
	}
}

func TestManagerStopTreatsFinishedProcessDuringKillAsStopped(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	_ = store.Save(state.ProcessRecord{Port: 4096, PID: 12345, StartedAt: time.Now().UTC(), TraceID: "trace_old"})
	manager := NewManager(cfg, store, &fakeStarter{pid: 12345}, &errorSignaler{killErr: syscall.ESRCH}, health.Checker{
		ProcessAlive: func(pid int) bool { return true },
	})

	result, err := manager.Stop(context.Background(), StopRequest{Port: 4096, TraceID: "trace_1234567890abcdef", Timeout: time.Millisecond})
	if err != nil {
		t.Fatalf("Stop should treat kill ESRCH as stopped, got error: %v", err)
	}
	if result.Status != StatusStopped {
		t.Fatalf("unexpected stop result %#v", result)
	}
	_, ok, err := store.Get(4096)
	if err != nil || ok {
		t.Fatalf("expected state to be removed, ok=%v err=%v", ok, err)
	}
}

func TestManagerRestartStopsThenStartsSamePort(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	_ = store.Save(state.ProcessRecord{Port: 4096, PID: 12345, StartedAt: time.Now().UTC(), TraceID: "trace_old"})
	starter := &fakeStarter{pid: 22345}
	manager := NewManager(cfg, store, starter, &recordingSignaler{}, health.Checker{
		ProcessAlive: func(pid int) bool { return false },
	})

	result, err := manager.Restart(context.Background(), StopRequest{Port: 4096, TraceID: "trace_1234567890abcdef", Timeout: time.Millisecond})
	if err != nil {
		t.Fatalf("Restart returned error: %v", err)
	}
	if result.Status != StatusStarted || result.PID != 22345 || len(starter.specs) != 1 {
		t.Fatalf("unexpected restart result %#v specs=%d", result, len(starter.specs))
	}
}

func TestManagerListRemovesDeadProcessRecords(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	_ = store.Save(state.ProcessRecord{Port: 4096, PID: 12345, StartedAt: time.Now().UTC(), TraceID: "trace_alive"})
	_ = store.Save(state.ProcessRecord{Port: 4097, PID: 22345, StartedAt: time.Now().UTC(), TraceID: "trace_dead"})
	manager := NewManager(cfg, store, &fakeStarter{pid: 12345}, fakeSignaler{}, health.Checker{
		ProcessAlive: func(pid int) bool { return pid == 12345 },
	})

	result, err := manager.List("trace_1234567890abcdef")
	if err != nil {
		t.Fatalf("List returned error: %v", err)
	}
	if len(result.Records) != 1 || result.Records[0].Port != 4096 {
		t.Fatalf("expected only alive record, got %#v", result.Records)
	}
	if _, ok, err := store.Get(4097); err != nil || ok {
		t.Fatalf("expected dead record to be removed, ok=%v err=%v", ok, err)
	}
}

func TestManagerHealthReturnsUnknownPortFailure(t *testing.T) {
	cfg := testConfig(t)
	manager := NewManager(cfg, state.NewFileStore(t.TempDir()), &fakeStarter{pid: 12345}, fakeSignaler{}, health.Checker{})

	result, err := manager.Health(context.Background(), HealthRequest{Port: 4096, TraceID: "trace_1234567890abcdef"})
	if err == nil {
		t.Fatalf("expected missing process error")
	}
	if result.Status != StatusFailed {
		t.Fatalf("expected failed result, got %#v", result)
	}
}

func testConfig(t *testing.T) config.Config {
	t.Helper()
	cfg := config.Config{
		ContainerID:   "ctr_01",
		LinuxServerID: "10.8.0.12",
		PortStart:     4096,
		PortEnd:       4100,
		MaxProcesses:  4,
		OpencodeBin:   "opencode",
		StateDir:      t.TempDir(),
		SessionRoot:   "/tmp/sessions",
		ConfigDir:     "/tmp/config/opencode/",
	}
	initializePublicConfigDir(t, cfg.ConfigDir)
	return cfg
}

func initializePublicConfigDir(t *testing.T, configDir string) {
	t.Helper()
	// 公共配置目录须由管理员预先初始化且非空，manager 不再自动创建。
	if err := os.MkdirAll(configDir, 0o755); err != nil {
		t.Fatalf("pre-create config dir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(configDir, ".testagent-public-config-ready"), []byte("ready"), 0o644); err != nil {
		t.Fatalf("write public config marker: %v", err)
	}
}

func assertPublicConfigRejected(t *testing.T, cfg config.Config) Result {
	t.Helper()
	store := state.NewFileStore(t.TempDir())
	starter := &fakeStarter{pid: 12345}
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{})

	result, err := manager.Start(context.Background(), StartRequest{Port: 4096, TraceID: "trace_1234567890abcdef"})
	if err == nil {
		t.Fatalf("expected public config dir error")
	}
	if result.Status != StatusFailed {
		t.Fatalf("expected failed result, got %#v", result)
	}
	if result.ErrorCode != "OPENCODE_UNAVAILABLE" {
		t.Fatalf("expected OPENCODE_UNAVAILABLE, got %q", result.ErrorCode)
	}
	if result.Message != "公共 opencode 配置尚未初始化。请使用超级管理员账号进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化后重试。" {
		t.Fatalf("unexpected failure message %q", result.Message)
	}
	if len(starter.specs) != 0 {
		t.Fatalf("starter must not be called when public config is unavailable, got %d specs", len(starter.specs))
	}
	return result
}

func equalStrings(left, right []string) bool {
	if len(left) != len(right) {
		return false
	}
	for i := range left {
		if left[i] != right[i] {
			return false
		}
	}
	return true
}

type fakeStarter struct {
	pid   int
	specs []StartSpec
}

func (f *fakeStarter) Start(_ context.Context, spec StartSpec) (int, error) {
	f.specs = append(f.specs, spec)
	return f.pid, nil
}

type recordingSignaler struct {
	terminated bool
	killed     bool
}

func (r *recordingSignaler) Terminate(pid int) error {
	r.terminated = pid == 12345
	return nil
}

func (r *recordingSignaler) Kill(pid int) error {
	r.killed = pid == 12345
	return nil
}

type fakeSignaler struct{}

func (fakeSignaler) Terminate(int) error { return nil }
func (fakeSignaler) Kill(int) error      { return nil }

type errorSignaler struct {
	terminateErr error
	killErr      error
}

func (s *errorSignaler) Terminate(int) error {
	if s.terminateErr != nil {
		return s.terminateErr
	}
	return nil
}

func (s *errorSignaler) Kill(int) error {
	if s.killErr != nil {
		return s.killErr
	}
	return nil
}
