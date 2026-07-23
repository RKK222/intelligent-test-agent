package process

import (
	"context"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"testing"
	"time"

	"github.com/enterprise/test-agent/opencode-manager/internal/config"
	"github.com/enterprise/test-agent/opencode-manager/internal/health"
	"github.com/enterprise/test-agent/opencode-manager/internal/state"
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

func TestBuildStartSpecPrefersExplicitSessionPath(t *testing.T) {
	cfg := testConfig(t)
	sessionPath := "/tmp/sessions/users/usr_1234567890abcdef"

	spec, err := BuildStartSpec(cfg, StartRequest{
		Port:        4096,
		SessionPath: sessionPath,
		TraceID:     "trace_1234567890abcdef",
	})
	if err != nil {
		t.Fatalf("BuildStartSpec returned error: %v", err)
	}

	if spec.SessionPath != sessionPath {
		t.Fatalf("expected explicit session path %q, got %q", sessionPath, spec.SessionPath)
	}
	if spec.Env["XDG_DATA_HOME"] != sessionPath {
		t.Fatalf("expected XDG_DATA_HOME from explicit session path, got %#v", spec.Env)
	}
	wantStartCommand := "XDG_DATA_HOME=/tmp/sessions/users/usr_1234567890abcdef OPENCODE_CONFIG_DIR=/tmp/config/opencode/ opencode serve --hostname 0.0.0.0 --port 4096 --print-logs"
	if spec.StartCommand != wantStartCommand {
		t.Fatalf("unexpected start command %q", spec.StartCommand)
	}
}

func TestBuildStartSpecPrefersExplicitManagedConfigPath(t *testing.T) {
	cfg := testConfig(t)
	managedConfig := "/tmp/sessions/users/usr_1234567890abcdef/.testagent-runtime/current-public-config"

	spec, err := BuildStartSpec(cfg, StartRequest{
		Port: 4096, ConfigPath: managedConfig, TraceID: "trace_1234567890abcdef",
	})
	if err != nil {
		t.Fatalf("BuildStartSpec returned error: %v", err)
	}
	if spec.ConfigPath != managedConfig || spec.Env["OPENCODE_CONFIG_DIR"] != managedConfig {
		t.Fatalf("expected explicit managed config path, spec=%#v", spec)
	}
}

func TestBuildStartSpecDisplaysReferencesDirWithoutExposingSensitiveEnvironment(t *testing.T) {
	cfg := testConfig(t)
	spec, err := BuildStartSpec(cfg, StartRequest{
		Port: 4096,
		Environment: map[string]string{
			"OPENCODE_REFERENCES_DIR":                 "/data/testagent/reference assets",
			"TEST_AGENT_INTERNAL_PROXY_API_KEY":       "proxy-secret",
			"TEST_AGENT_INTERNAL_PROXY_BASE_URL":      "http://127.0.0.1:8080/proxy",
			"ENTERPRISE_UCID":                         "employee-001",
			"UNLISTED_SENSITIVE_ENVIRONMENT_VARIABLE": "must-not-be-displayed",
		},
		TraceID: "trace_1234567890abcdef",
	})
	if err != nil {
		t.Fatalf("BuildStartSpec returned error: %v", err)
	}

	// 子进程环境继续完整透传；startCommand 只展示允许项并保持代理密钥脱敏。
	if spec.Env["OPENCODE_REFERENCES_DIR"] != "/data/testagent/reference assets" {
		t.Fatalf("expected references dir in child env, got %#v", spec.Env)
	}
	if spec.Env["UNLISTED_SENSITIVE_ENVIRONMENT_VARIABLE"] != "must-not-be-displayed" {
		t.Fatalf("expected unlisted environment to reach child process, got %#v", spec.Env)
	}
	if !strings.Contains(spec.StartCommand, "OPENCODE_REFERENCES_DIR='/data/testagent/reference assets'") {
		t.Fatalf("expected references dir in safe start command, got %q", spec.StartCommand)
	}
	if !strings.Contains(spec.StartCommand, "TEST_AGENT_INTERNAL_PROXY_API_KEY='<redacted>'") {
		t.Fatalf("expected proxy api key to remain redacted, got %q", spec.StartCommand)
	}
	if strings.Contains(spec.StartCommand, "proxy-secret") ||
		strings.Contains(spec.StartCommand, "UNLISTED_SENSITIVE_ENVIRONMENT_VARIABLE") ||
		strings.Contains(spec.StartCommand, "must-not-be-displayed") {
		t.Fatalf("start command exposed sensitive environment: %q", spec.StartCommand)
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

	result, err := manager.Start(context.Background(), StartRequest{
		Port:          4096,
		UnifiedAuthID: "DEV_888888888",
		SessionPath:   "/tmp/sessions/users/DEV_888888888",
		TraceID:       "trace_1234567890abcdef",
	})
	if err != nil {
		t.Fatalf("Start returned error: %v", err)
	}
	if result.Status != StatusStarted || result.PID != 12345 {
		t.Fatalf("unexpected start result %#v", result)
	}
	if !result.ProcessCreated {
		t.Fatalf("fresh start must report processCreated=true, got %#v", result)
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
	if record.UnifiedAuthID != "DEV_888888888" || !record.StartedAt.Equal(starter.specs[0].StartedAt) {
		t.Fatalf("expected state identity and timestamp from start spec, record=%#v spec=%#v", record, starter.specs[0])
	}
	wantLogBase := "DEV_888888888-" + record.StartedAt.Format(logTimestampLayout) + "-4096.log"
	if filepath.Base(starter.specs[0].LogPath) != wantLogBase {
		t.Fatalf("expected log basename %q, got %q", wantLogBase, starter.specs[0].LogPath)
	}
	reused, err := manager.Start(context.Background(), StartRequest{
		Port:          4096,
		UnifiedAuthID: "DEV_888888888",
		SessionPath:   "/tmp/sessions/users/DEV_888888888",
		TraceID:       "trace_1234567890abcdef",
	})
	if err != nil {
		t.Fatalf("Start should reuse healthy managed port, got error: %v", err)
	}
	if reused.Status != StatusStarted || reused.PID != 12345 {
		t.Fatalf("unexpected reused result %#v", reused)
	}
	if reused.ProcessCreated {
		t.Fatalf("idempotent reuse must report processCreated=false, got %#v", reused)
	}
	if len(starter.specs) != 1 {
		t.Fatalf("expected duplicate start to reuse existing process, got %d starts", len(starter.specs))
	}
}

func TestManagerStartReusesHealthyLegacyRecordWithIdentityDerivedFromSessionPath(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		response.WriteHeader(http.StatusOK)
	}))
	defer server.Close()
	sessionPath := "/tmp/sessions/users/legacy-user"
	if err := store.Save(state.ProcessRecord{
		Port: 4096, PID: 12345, BaseURL: server.URL,
		SessionPath: sessionPath, ConfigPath: cfg.ConfigDir,
		StartedAt: time.Now().UTC(), TraceID: "trace_old",
	}); err != nil {
		t.Fatalf("pre-save legacy process state: %v", err)
	}
	starter := &fakeStarter{pid: 22345}
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{
		ProcessAlive: func(pid int) bool { return true },
		Client:       server.Client(), ProbeBaseURL: server.URL,
	})

	result, err := manager.Start(context.Background(), StartRequest{
		Port: 4096, SessionPath: sessionPath, ConfigPath: cfg.ConfigDir, TraceID: "trace_1234567890abcdef",
	})
	if err != nil {
		t.Fatalf("legacy healthy record should be reused, result=%#v err=%v", result, err)
	}
	if result.Status != StatusStarted || result.PID != 12345 {
		t.Fatalf("unexpected legacy reuse result %#v", result)
	}
	if len(starter.specs) != 0 {
		t.Fatalf("starter must not be called for a healthy legacy record, got %d calls", len(starter.specs))
	}
}

func TestManagerStartRejectsHealthyManagedPortForDifferentUnifiedAuthID(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		response.WriteHeader(http.StatusOK)
	}))
	defer server.Close()
	if err := store.Save(state.ProcessRecord{
		Port: 4096, PID: 12345, BaseURL: server.URL,
		UnifiedAuthID: "user-managed", SessionPath: "/tmp/sessions/users/user-managed", ConfigPath: cfg.ConfigDir,
		StartedAt: time.Now().UTC(), TraceID: "trace_old",
	}); err != nil {
		t.Fatalf("pre-save process state: %v", err)
	}
	starter := &fakeStarter{pid: 22345}
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{
		ProcessAlive: func(pid int) bool { return true },
		Client:       server.Client(), ProbeBaseURL: server.URL,
	})

	result, err := manager.Start(context.Background(), StartRequest{
		Port: 4096, UnifiedAuthID: "user-requested", SessionPath: "/tmp/sessions/users/user-requested",
		ConfigPath: cfg.ConfigDir, TraceID: "trace_1234567890abcdef",
	})
	if err == nil {
		t.Fatalf("expected different identity to be rejected, result=%#v", result)
	}
	if result.ErrorCode != "PORT_CONFLICT" {
		t.Fatalf("expected PORT_CONFLICT, got %#v", result)
	}
	if len(starter.specs) != 0 {
		t.Fatalf("starter must not be called for an identity conflict, got %d calls", len(starter.specs))
	}
}

func TestManagerStartRejectsIdentityAlreadyManagedAtAnotherPort(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	if err := store.Save(state.ProcessRecord{
		Port: 4097, PID: 12345, UnifiedAuthID: "user-managed",
		SessionPath: "/tmp/sessions/users/user-managed", ConfigPath: cfg.ConfigDir,
		StartedAt: time.Now().UTC(), TraceID: "trace_old",
	}); err != nil {
		t.Fatalf("pre-save process state: %v", err)
	}
	starter := &fakeStarter{pid: 22345}
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{})

	result, err := manager.Start(context.Background(), StartRequest{
		Port: 4096, UnifiedAuthID: " user-managed ", SessionPath: "/tmp/sessions/users/user-managed",
		ConfigPath: cfg.ConfigDir, TraceID: "trace_1234567890abcdef",
	})
	if err == nil {
		t.Fatalf("expected managed identity to be rejected, result=%#v", result)
	}
	if result.ErrorCode != "IDENTITY_ALREADY_MANAGED" {
		t.Fatalf("expected IDENTITY_ALREADY_MANAGED, got %#v", result)
	}
	if len(starter.specs) != 0 {
		t.Fatalf("starter must not be called for an already managed identity, got %d calls", len(starter.specs))
	}
}

func TestManagerStartRejectsIdentityDerivedFromLegacyRecordAtAnotherPort(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	sessionPath := "/tmp/sessions/users/legacy-user"
	if err := store.Save(state.ProcessRecord{
		Port: 4097, PID: 12345, SessionPath: sessionPath, ConfigPath: cfg.ConfigDir,
		StartedAt: time.Now().UTC(), TraceID: "trace_old",
	}); err != nil {
		t.Fatalf("pre-save legacy process state: %v", err)
	}
	starter := &fakeStarter{pid: 22345}
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{})

	result, err := manager.Start(context.Background(), StartRequest{
		Port: 4096, SessionPath: sessionPath, ConfigPath: cfg.ConfigDir, TraceID: "trace_1234567890abcdef",
	})
	if err == nil {
		t.Fatalf("expected identity derived from legacy state to be rejected, result=%#v", result)
	}
	if result.ErrorCode != "IDENTITY_ALREADY_MANAGED" {
		t.Fatalf("expected IDENTITY_ALREADY_MANAGED, got %#v", result)
	}
	if len(starter.specs) != 0 {
		t.Fatalf("starter must not be called for a legacy managed identity, got %d calls", len(starter.specs))
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

func TestManagerStartAllowsExplicitBindingRecoveryWhenCapacityIsFull(t *testing.T) {
	cfg := testConfig(t)
	cfg.MaxProcesses = 1
	store := state.NewFileStore(t.TempDir())
	if err := store.Save(state.ProcessRecord{
		Port: 4097, PID: 22345, UnifiedAuthID: "other-user",
		SessionPath: "/tmp/sessions/users/other-user", ConfigPath: cfg.ConfigDir,
		StartedAt: time.Now().UTC(), TraceID: "trace_old",
	}); err != nil {
		t.Fatalf("pre-save process state: %v", err)
	}
	starter := &fakeStarter{pid: 12345}
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{})

	result, err := manager.Start(context.Background(), StartRequest{
		Port: 4096, UnifiedAuthID: "bound-user", SessionPath: "/tmp/sessions/users/bound-user",
		ConfigPath: cfg.ConfigDir, BindingRecovery: true, TraceID: "trace_1234567890abcdef",
	})

	if err != nil {
		t.Fatalf("explicit binding recovery must bypass scheduling capacity: result=%#v err=%v", result, err)
	}
	if result.Status != StatusStarted || result.PID != 12345 || !result.ProcessCreated {
		t.Fatalf("unexpected binding recovery result %#v", result)
	}
	if len(starter.specs) != 1 || starter.specs[0].Port != 4096 {
		t.Fatalf("expected one exact-port recovery start, got %#v", starter.specs)
	}
}

func TestManagerStartRejectsOutOfRangePortWithStableErrorCode(t *testing.T) {
	cfg := testConfig(t)
	starter := &fakeStarter{pid: 12345}
	manager := NewManager(cfg, state.NewFileStore(t.TempDir()), starter, fakeSignaler{}, health.Checker{})

	result, err := manager.Start(context.Background(), StartRequest{Port: 4101, TraceID: "trace_1234567890abcdef"})
	if err == nil {
		t.Fatalf("expected out-of-range port to be rejected, result=%#v", result)
	}
	if result.ErrorCode != "PORT_OUT_OF_RANGE" {
		t.Fatalf("expected PORT_OUT_OF_RANGE, got %#v", result)
	}
	if len(starter.specs) != 0 {
		t.Fatalf("starter must not be called for an out-of-range port, got %d calls", len(starter.specs))
	}
}

func TestManagerStartRejectsUnmanagedListeningPortBeforeStarting(t *testing.T) {
	listener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("open temporary listener: %v", err)
	}
	defer listener.Close()
	port := listener.Addr().(*net.TCPAddr).Port
	cfg := testConfig(t)
	cfg.PortStart = port
	cfg.PortEnd = port
	cfg.MaxProcesses = 1
	starter := &fakeStarter{pid: 12345}
	manager := NewManager(cfg, state.NewFileStore(t.TempDir()), starter, fakeSignaler{}, health.Checker{})

	result, err := manager.Start(context.Background(), StartRequest{Port: port, TraceID: "trace_1234567890abcdef"})
	if err == nil {
		t.Fatalf("expected occupied listener to be rejected, result=%#v", result)
	}
	if result.ErrorCode != "PORT_CONFLICT" {
		t.Fatalf("expected PORT_CONFLICT, got %#v", result)
	}
	if len(starter.specs) != 0 {
		t.Fatalf("starter must not be called for an occupied listener, got %d calls", len(starter.specs))
	}
}

func TestManagerStartRejectsListenerBoundToNonLoopbackIPv4(t *testing.T) {
	host := nonLoopbackIPv4(t)
	listener, err := net.Listen("tcp4", net.JoinHostPort(host, "0"))
	if err != nil {
		t.Fatalf("open non-loopback temporary listener: %v", err)
	}
	defer listener.Close()
	port := listener.Addr().(*net.TCPAddr).Port
	cfg := testConfig(t)
	cfg.PortStart = port
	cfg.PortEnd = port
	cfg.MaxProcesses = 1
	starter := &fakeStarter{pid: 12345}
	manager := NewManager(cfg, state.NewFileStore(t.TempDir()), starter, fakeSignaler{}, health.Checker{})

	result, err := manager.Start(context.Background(), StartRequest{Port: port, TraceID: "trace_1234567890abcdef"})
	if err == nil {
		t.Fatalf("expected non-loopback listener conflict, result=%#v", result)
	}
	if result.ErrorCode != "PORT_CONFLICT" {
		t.Fatalf("expected PORT_CONFLICT, got %#v", result)
	}
	if len(starter.specs) != 0 {
		t.Fatalf("starter must not be called for a non-loopback listener, got %d calls", len(starter.specs))
	}
}

func TestManagerSerializesConcurrentStartsForSameIdentityAndPort(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		response.WriteHeader(http.StatusOK)
	}))
	defer server.Close()
	starter := newBlockingStarter(12345)
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{
		ProcessAlive: func(pid int) bool { return true },
		Client:       server.Client(), ProbeBaseURL: server.URL,
	})
	request := StartRequest{
		Port: 4096, UnifiedAuthID: "user-managed", SessionPath: "/tmp/sessions/users/user-managed",
		ConfigPath: cfg.ConfigDir, TraceID: "trace_1234567890abcdef",
	}
	results := make(chan Result, 2)
	errors := make(chan error, 2)
	for range 2 {
		go func() {
			result, err := manager.Start(context.Background(), request)
			results <- result
			errors <- err
		}()
	}

	select {
	case <-starter.entered:
	case <-time.After(time.Second):
		t.Fatal("first starter call did not begin")
	}
	select {
	case <-starter.secondCall:
		t.Fatal("concurrent start invoked Starter.Start before the first start completed")
	case <-time.After(100 * time.Millisecond):
	}
	close(starter.release)
	for range 2 {
		if err := <-errors; err != nil {
			t.Fatalf("concurrent Start returned error: %v", err)
		}
		if result := <-results; result.Status != StatusStarted || result.PID != 12345 {
			t.Fatalf("unexpected concurrent start result %#v", result)
		}
	}
	if calls := starter.CallCount(); calls != 1 {
		t.Fatalf("expected one OS start after concurrent commands, got %d", calls)
	}
}

func TestManagerStartRejectsHealthyExistingProcessWithDifferentExplicitConfigPath(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		response.WriteHeader(http.StatusOK)
	}))
	defer server.Close()
	if err := store.Save(state.ProcessRecord{
		Port: 4096, PID: 12345, BaseURL: server.URL,
		UnifiedAuthID: "user-managed", SessionPath: "/tmp/sessions/users/user-managed", ConfigPath: cfg.ConfigDir,
		StartedAt: time.Now().UTC(), TraceID: "trace_old",
	}); err != nil {
		t.Fatalf("pre-save process state: %v", err)
	}
	manager := NewManager(cfg, store, &fakeStarter{pid: 22345}, fakeSignaler{}, health.Checker{
		ProcessAlive: func(pid int) bool { return true },
		Client:       server.Client(), ProbeBaseURL: server.URL,
	})

	result, err := manager.Start(context.Background(), StartRequest{
		Port: 4096, UnifiedAuthID: "user-managed", SessionPath: "/tmp/sessions/users/user-managed", ConfigPath: "/tmp/session/current-public-config",
		TraceID: "trace_1234567890abcdef",
	})
	if err == nil {
		t.Fatalf("expected explicit config mismatch, result=%#v err=%v", result, err)
	}
	if result.ErrorCode != "IDENTITY_CONFIG_MISMATCH" {
		t.Fatalf("expected IDENTITY_CONFIG_MISMATCH, got %#v", result)
	}
}

func TestManagerStartRejectsHealthyExistingProcessWithDifferentSessionPath(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		response.WriteHeader(http.StatusOK)
	}))
	defer server.Close()
	if err := store.Save(state.ProcessRecord{
		Port: 4096, PID: 12345, BaseURL: server.URL,
		UnifiedAuthID: "user-managed", SessionPath: "/tmp/sessions/users/user-managed", ConfigPath: cfg.ConfigDir,
		StartedAt: time.Now().UTC(), TraceID: "trace_old",
	}); err != nil {
		t.Fatalf("pre-save process state: %v", err)
	}
	starter := &fakeStarter{pid: 22345}
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{
		ProcessAlive: func(pid int) bool { return true },
		Client:       server.Client(), ProbeBaseURL: server.URL,
	})

	result, err := manager.Start(context.Background(), StartRequest{
		Port: 4096, UnifiedAuthID: "user-managed", SessionPath: "/tmp/other-sessions/users/user-managed",
		ConfigPath: cfg.ConfigDir, TraceID: "trace_1234567890abcdef",
	})
	if err == nil {
		t.Fatalf("expected explicit session mismatch, result=%#v", result)
	}
	if result.ErrorCode != "IDENTITY_CONFIG_MISMATCH" {
		t.Fatalf("expected IDENTITY_CONFIG_MISMATCH, got %#v", result)
	}
	if len(starter.specs) != 0 {
		t.Fatalf("starter must not be called for a session mismatch, got %d calls", len(starter.specs))
	}
}

func TestManagerStartChecksIdentityPathsBeforeUnhealthyState(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	if err := store.Save(state.ProcessRecord{
		Port: 4096, PID: 12345, UnifiedAuthID: "user-managed",
		SessionPath: "/tmp/sessions/users/user-managed", ConfigPath: cfg.ConfigDir,
		StartedAt: time.Now().UTC(), TraceID: "trace_old",
	}); err != nil {
		t.Fatalf("pre-save process state: %v", err)
	}
	starter := &fakeStarter{pid: 22345}
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{
		ProcessAlive: func(pid int) bool { return false },
	})

	result, err := manager.Start(context.Background(), StartRequest{
		Port: 4096, UnifiedAuthID: "user-managed", SessionPath: "/tmp/other-sessions/users/user-managed",
		ConfigPath: cfg.ConfigDir, TraceID: "trace_1234567890abcdef",
	})
	if err == nil {
		t.Fatalf("expected mismatched session path to be rejected, result=%#v", result)
	}
	if result.ErrorCode != "IDENTITY_CONFIG_MISMATCH" {
		t.Fatalf("expected IDENTITY_CONFIG_MISMATCH before unhealthy result, got %#v", result)
	}
	if len(starter.specs) != 0 {
		t.Fatalf("starter must not be called for a mismatched unhealthy record, got %d calls", len(starter.specs))
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

func TestManagerApplyRuntimeConfigSupportsMaxOnlyUpdateAfterFullConfig(t *testing.T) {
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

	if _, err := manager.ApplyRuntimeConfig(4, sessionRoot, configDir); err != nil {
		t.Fatalf("initial full runtime config failed: %v", err)
	}
	applied, err := manager.ApplyRuntimeConfig(2, "", "")
	if err != nil {
		t.Fatalf("max-only runtime config should be accepted after full config: %v", err)
	}
	if applied != 2 || manager.MaxProcesses() != 2 {
		t.Fatalf("expected max-only update to apply 2, applied=%d current=%d", applied, manager.MaxProcesses())
	}
	result, err := manager.Start(context.Background(), StartRequest{Port: 4096, TraceID: "trace_1234567890abcdef"})
	if err != nil {
		t.Fatalf("Start returned error after max-only config: %v", err)
	}
	if result.SessionPath != sessionRoot+"4096" {
		t.Fatalf("max-only config must keep previous session path, got %q", result.SessionPath)
	}
	if result.ConfigPath != configDir {
		t.Fatalf("max-only config must keep previous config path, got %q", result.ConfigPath)
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

func TestManagerStopOwnedRejectsReusedPortWithoutSignalsOrStateDeletion(t *testing.T) {
	cases := []struct {
		name              string
		managedUnifiedID  string
		expectedUnifiedID string
	}{
		{
			name:              "another user now owns the port",
			managedUnifiedID:  "user-b",
			expectedUnifiedID: "user-a",
		},
		{
			name:              "same user has a newer pid",
			managedUnifiedID:  "user-a",
			expectedUnifiedID: "user-a",
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			cfg := testConfig(t)
			store := state.NewFileStore(t.TempDir())
			if err := store.Save(state.ProcessRecord{
				Port:          4096,
				PID:           200,
				UnifiedAuthID: tc.managedUnifiedID,
				SessionPath:   "/tmp/sessions/users/" + tc.managedUnifiedID,
				StartedAt:     time.Now().UTC(),
				TraceID:       "trace_new",
			}); err != nil {
				t.Fatalf("save replacement state: %v", err)
			}
			signaler := &capturingSignaler{}
			manager := NewManager(cfg, store, &fakeStarter{pid: 12345}, signaler, health.Checker{})

			result, err := manager.StopOwned(context.Background(), OwnedStopRequest{
				Port:                  4096,
				ExpectedUnifiedAuthID: tc.expectedUnifiedID,
				ExpectedPID:           100,
				TraceID:               "trace_stale_stop",
				Timeout:               time.Millisecond,
			})

			if err == nil {
				t.Fatalf("expected stale owned stop to fail, result=%#v", result)
			}
			if result.ErrorCode != "PROCESS_OWNERSHIP_MISMATCH" {
				t.Fatalf("expected stable ownership mismatch, got %#v", result)
			}
			if strings.Contains(result.Message, "user-a") || strings.Contains(result.Message, "user-b") {
				t.Fatalf("ownership mismatch must not expose UCID, got %q", result.Message)
			}
			if len(signaler.terminatedPIDs) != 0 || len(signaler.killedPIDs) != 0 {
				t.Fatalf("stale stop must not signal replacement process, signaler=%#v", signaler)
			}
			record, ok, getErr := store.Get(4096)
			if getErr != nil || !ok || record.PID != 200 || record.UnifiedAuthID != tc.managedUnifiedID {
				t.Fatalf("replacement state must remain intact, record=%#v ok=%t err=%v", record, ok, getErr)
			}
		})
	}
}

func TestManagerStopOwnedUsesLegacySessionIdentityAndFailsClosedWhenUnavailable(t *testing.T) {
	t.Run("derives legacy identity from users session path", func(t *testing.T) {
		cfg := testConfig(t)
		store := state.NewFileStore(t.TempDir())
		if err := store.Save(state.ProcessRecord{
			Port:        4096,
			PID:         100,
			SessionPath: "/tmp/sessions/users/user-a",
			StartedAt:   time.Now().UTC(),
			TraceID:     "trace_legacy",
		}); err != nil {
			t.Fatalf("save legacy state: %v", err)
		}
		signaler := &capturingSignaler{}
		manager := NewManager(cfg, store, &fakeStarter{pid: 12345}, signaler, health.Checker{
			ProcessAlive: func(int) bool { return false },
		})

		result, err := manager.StopOwned(context.Background(), OwnedStopRequest{
			Port:                  4096,
			ExpectedUnifiedAuthID: "user-a",
			ExpectedPID:           100,
			TraceID:               "trace_owned_stop",
			Timeout:               time.Millisecond,
		})

		if err != nil || result.Status != StatusStopped {
			t.Fatalf("matching legacy identity should stop, result=%#v err=%v", result, err)
		}
		if len(signaler.terminatedPIDs) != 1 || signaler.terminatedPIDs[0] != 100 {
			t.Fatalf("expected only legacy pid 100 to receive terminate, got %#v", signaler.terminatedPIDs)
		}
		if _, ok, getErr := store.Get(4096); getErr != nil || ok {
			t.Fatalf("matching stopped state should be deleted, ok=%t err=%v", ok, getErr)
		}
	})

	t.Run("missing legacy identity is rejected before signal", func(t *testing.T) {
		cfg := testConfig(t)
		store := state.NewFileStore(t.TempDir())
		if err := store.Save(state.ProcessRecord{
			Port:        4096,
			PID:         100,
			SessionPath: "/tmp/legacy/session-4096",
			StartedAt:   time.Now().UTC(),
			TraceID:     "trace_legacy",
		}); err != nil {
			t.Fatalf("save legacy state: %v", err)
		}
		signaler := &capturingSignaler{}
		manager := NewManager(cfg, store, &fakeStarter{pid: 12345}, signaler, health.Checker{})

		result, err := manager.StopOwned(context.Background(), OwnedStopRequest{
			Port:                  4096,
			ExpectedUnifiedAuthID: "user-a",
			ExpectedPID:           100,
			TraceID:               "trace_owned_stop",
			Timeout:               time.Millisecond,
		})

		if err == nil || result.ErrorCode != "PROCESS_OWNERSHIP_MISMATCH" {
			t.Fatalf("unverifiable legacy state must fail closed, result=%#v err=%v", result, err)
		}
		if len(signaler.terminatedPIDs) != 0 || len(signaler.killedPIDs) != 0 {
			t.Fatalf("unverifiable state must not be signaled, signaler=%#v", signaler)
		}
		if _, ok, getErr := store.Get(4096); getErr != nil || !ok {
			t.Fatalf("unverifiable state must remain, ok=%t err=%v", ok, getErr)
		}
	})
}

func TestManagerRestartPreservesStoredSessionPath(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	sessionPath := "/tmp/sessions/users/usr_1234567890abcdef"
	if err := store.Save(state.ProcessRecord{
		Port:          4096,
		PID:           12345,
		BaseURL:       "http://10.8.0.12:4096",
		SessionPath:   sessionPath,
		UnifiedAuthID: "usr_1234567890abcdef",
		ConfigPath:    cfg.ConfigDir,
		StartedAt:     time.Now().UTC(),
		StartCommand:  "old",
		TraceID:       "trace_old",
	}); err != nil {
		t.Fatalf("pre-save process state: %v", err)
	}
	starter := &fakeStarter{pid: 22345}
	manager := NewManager(cfg, store, starter, fakeSignaler{}, health.Checker{
		ProcessAlive: func(pid int) bool { return false },
	})

	result, err := manager.Restart(context.Background(), StopRequest{
		Port:    4096,
		TraceID: "trace_1234567890abcdef",
		Timeout: time.Millisecond,
	})
	if err != nil {
		t.Fatalf("Restart returned error: %v", err)
	}

	if result.SessionPath != sessionPath {
		t.Fatalf("expected restart result to keep session path %q, got %q", sessionPath, result.SessionPath)
	}
	expectedConfigPath := filepath.Clean(cfg.ConfigDir)
	if result.ConfigPath != expectedConfigPath {
		t.Fatalf("expected restart result to keep config path %q, got %q", expectedConfigPath, result.ConfigPath)
	}
	if len(starter.specs) != 1 {
		t.Fatalf("expected one start after stop, got %d", len(starter.specs))
	}
	if starter.specs[0].SessionPath != sessionPath {
		t.Fatalf("expected restart to use stored session path %q, got %q", sessionPath, starter.specs[0].SessionPath)
	}
	if starter.specs[0].ConfigPath != expectedConfigPath {
		t.Fatalf("expected restart to use stored config path %q, got %q", expectedConfigPath, starter.specs[0].ConfigPath)
	}
	if starter.specs[0].UnifiedAuthID != "usr_1234567890abcdef" {
		t.Fatalf("expected restart to preserve unified auth id, got %#v", starter.specs[0])
	}
	record, ok, err := store.Get(4096)
	if err != nil || !ok {
		t.Fatalf("expected restarted state, ok=%t err=%v", ok, err)
	}
	if record.UnifiedAuthID != "usr_1234567890abcdef" || !record.StartedAt.Equal(starter.specs[0].StartedAt) {
		t.Fatalf("expected restarted state to match start spec, record=%#v spec=%#v", record, starter.specs[0])
	}
	wantLogBase := "usr_1234567890abcdef-" + record.StartedAt.Format(logTimestampLayout) + "-4096.log"
	if filepath.Base(starter.specs[0].LogPath) != wantLogBase {
		t.Fatalf("expected restart log basename %q, got %q", wantLogBase, starter.specs[0].LogPath)
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
		ProcessAlive: func(pid int) bool { return !signaler.killed },
	})

	_, err := manager.Stop(context.Background(), StopRequest{Port: 4096, TraceID: "trace_1234567890abcdef", Timeout: time.Millisecond})
	if err != nil {
		t.Fatalf("Stop returned error: %v", err)
	}
	if !signaler.terminated || !signaler.killed {
		t.Fatalf("expected SIGTERM and SIGKILL to be sent, got terminated=%v killed=%v", signaler.terminated, signaler.killed)
	}
	if _, ok, getErr := store.Get(4096); getErr != nil || ok {
		t.Fatalf("confirmed stopped process state should be removed, ok=%t err=%v", ok, getErr)
	}
}

func TestManagerStopKeepsStateWhenProcessRemainsAliveAfterKill(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	_ = store.Save(state.ProcessRecord{Port: 4096, PID: 12345, StartedAt: time.Now().UTC(), TraceID: "trace_old"})
	signaler := &recordingSignaler{}
	manager := NewManager(cfg, store, &fakeStarter{pid: 12345}, signaler, health.Checker{
		ProcessAlive: func(pid int) bool { return true },
	})

	result, err := manager.Stop(context.Background(), StopRequest{
		Port: 4096, TraceID: "trace_process_still_alive", Timeout: time.Millisecond,
	})

	if err == nil || result.Status != StatusFailed {
		t.Fatalf("stop must fail while the killed PID remains alive, result=%#v err=%v", result, err)
	}
	if !signaler.terminated || !signaler.killed {
		t.Fatalf("expected SIGTERM and SIGKILL before confirmation failure, signaler=%#v", signaler)
	}
	record, ok, getErr := store.Get(4096)
	if getErr != nil || !ok || record.PID != 12345 {
		t.Fatalf("unconfirmed process state must remain authoritative, record=%#v ok=%t err=%v", record, ok, getErr)
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

func TestManagerListDoesNotDeleteReplacementWrittenByConcurrentRestart(t *testing.T) {
	cfg := testConfig(t)
	store := state.NewFileStore(t.TempDir())
	_ = store.Save(state.ProcessRecord{
		Port: 4096, PID: 12345, UnifiedAuthID: "user-a",
		SessionPath: "/tmp/sessions/users/user-a", ConfigPath: cfg.ConfigDir,
		StartedAt: time.Now().UTC(), TraceID: "trace_old",
	})
	checkingOldPID := make(chan struct{})
	releaseOldCheck := make(chan struct{})
	var oldCheckMu sync.Mutex
	oldChecks := 0
	manager := NewManager(cfg, store, &fakeStarter{pid: 22345}, fakeSignaler{}, health.Checker{
		ProcessAlive: func(pid int) bool {
			if pid == 22345 {
				return true
			}
			oldCheckMu.Lock()
			oldChecks++
			checkNumber := oldChecks
			oldCheckMu.Unlock()
			if checkNumber == 1 {
				close(checkingOldPID)
				<-releaseOldCheck
			}
			return false
		},
	})

	type listOutcome struct {
		result Result
		err    error
	}
	listed := make(chan listOutcome, 1)
	go func() {
		result, err := manager.List("trace_heartbeat")
		listed <- listOutcome{result: result, err: err}
	}()
	<-checkingOldPID

	restarted := make(chan error, 1)
	go func() {
		_, err := manager.Restart(context.Background(), StopRequest{
			Port: 4096, TraceID: "trace_restart", Timeout: time.Millisecond,
		})
		restarted <- err
	}()
	select {
	case err := <-restarted:
		if err != nil {
			t.Fatalf("concurrent restart returned error: %v", err)
		}
	case <-time.After(time.Second):
		t.Fatal("restart should complete while heartbeat is checking its stale PID snapshot")
	}
	replacement, ok, err := store.Get(4096)
	if err != nil || !ok || replacement.PID != 22345 {
		t.Fatalf("expected restart to write replacement state before stale check resumes, record=%#v ok=%t err=%v", replacement, ok, err)
	}

	close(releaseOldCheck)
	outcome := <-listed
	if outcome.err != nil {
		t.Fatalf("List returned error: %v", outcome.err)
	}
	replacement, ok, err = store.Get(4096)
	if err != nil || !ok || replacement.PID != 22345 {
		t.Fatalf("stale heartbeat must not delete replacement state, record=%#v ok=%t err=%v", replacement, ok, err)
	}
	if len(outcome.result.Records) != 1 || outcome.result.Records[0].PID != 22345 {
		t.Fatalf("heartbeat should report the replacement process, got %#v", outcome.result.Records)
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
	if result.ErrorCode != "PROCESS_NOT_MANAGED" {
		t.Fatalf("expected stable PROCESS_NOT_MANAGED errorCode, got %#v", result)
	}
}

func testConfig(t *testing.T) config.Config {
	t.Helper()
	cfg := config.Config{
		ContainerID:   "ctr_01",
		ContainerName: "test-agent-opencode-worker",
		LinuxServerID: "test-agent-backend-10-8-0-12",
		ServerHost:    "10.8.0.12",
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
	wantMessage := "服务器" + cfg.LinuxServerID + "，公共 opencode 配置目录" + cfg.ConfigDir + "尚未初始化。请联系超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化后重试。"
	if result.Message != wantMessage {
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

func nonLoopbackIPv4(t *testing.T) string {
	t.Helper()
	interfaces, err := net.Interfaces()
	if err != nil {
		t.Fatalf("list network interfaces: %v", err)
	}
	for _, iface := range interfaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addresses, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, address := range addresses {
			ipNet, ok := address.(*net.IPNet)
			if !ok || ipNet.IP.IsLoopback() {
				continue
			}
			if ipv4 := ipNet.IP.To4(); ipv4 != nil {
				return ipv4.String()
			}
		}
	}
	t.Skip("test host has no non-loopback IPv4 address")
	return ""
}

type fakeStarter struct {
	pid   int
	specs []StartSpec
}

func (f *fakeStarter) Start(_ context.Context, spec StartSpec) (int, error) {
	f.specs = append(f.specs, spec)
	return f.pid, nil
}

type blockingStarter struct {
	pid        int
	entered    chan struct{}
	secondCall chan struct{}
	release    chan struct{}
	mu         sync.Mutex
	calls      int
}

func newBlockingStarter(pid int) *blockingStarter {
	return &blockingStarter{
		pid:        pid,
		entered:    make(chan struct{}),
		secondCall: make(chan struct{}),
		release:    make(chan struct{}),
	}
}

func (s *blockingStarter) Start(_ context.Context, _ StartSpec) (int, error) {
	s.mu.Lock()
	s.calls++
	calls := s.calls
	s.mu.Unlock()
	if calls == 1 {
		close(s.entered)
	} else {
		close(s.secondCall)
	}
	<-s.release
	return s.pid, nil
}

func (s *blockingStarter) CallCount() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.calls
}

type recordingSignaler struct {
	terminated bool
	killed     bool
}

type capturingSignaler struct {
	terminatedPIDs []int
	killedPIDs     []int
}

func (s *capturingSignaler) Terminate(pid int) error {
	s.terminatedPIDs = append(s.terminatedPIDs, pid)
	return nil
}

func (s *capturingSignaler) Kill(pid int) error {
	s.killedPIDs = append(s.killedPIDs, pid)
	return nil
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
