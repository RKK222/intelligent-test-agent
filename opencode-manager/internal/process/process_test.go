package process

import (
	"context"
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

func TestManagerStartWritesStateAndRejectsOccupiedPort(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	starter := &fakeStarter{pid: 12345}
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{ProcessAlive: func(pid int) bool { return true }})

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
	if _, err := manager.Start(context.Background(), StartRequest{Port: 4096, TraceID: "trace_1234567890abcdef"}); err == nil {
		t.Fatalf("expected occupied port error")
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
