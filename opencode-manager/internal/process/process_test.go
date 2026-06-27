package process

import (
	"context"
	"net/http"
	"net/http/httptest"
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
	return config.Config{
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
