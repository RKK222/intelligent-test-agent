package control

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/icbc/test-agent/opencode-manager/internal/config"
	"github.com/icbc/test-agent/opencode-manager/internal/health"
	"github.com/icbc/test-agent/opencode-manager/internal/process"
	"github.com/icbc/test-agent/opencode-manager/internal/state"
	"nhooyr.io/websocket"
)

func TestRestartStopTimeoutReservesHalfOfCommandBudgetForStartup(t *testing.T) {
	if got := restartStopTimeout(10 * time.Second); got != 5*time.Second {
		t.Fatalf("expected 5s stop timeout, got %s", got)
	}
}

func TestDispatchStartCommandPassesSessionPathToProcessManager(t *testing.T) {
	configDir := filepath.Join(t.TempDir(), "opencode-config")
	if err := os.MkdirAll(configDir, 0o755); err != nil {
		t.Fatalf("pre-create config dir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(configDir, ".ready"), []byte("ready"), 0o644); err != nil {
		t.Fatalf("write config marker: %v", err)
	}
	starter := &capturingStarter{pid: 12345}
	manager := process.NewManager(
		config.Config{
			ContainerID:   "ctr_01",
			LinuxServerID: "test-agent-backend-10-8-0-12",
			ServerHost:    "10.8.0.12",
			PortStart:     4096,
			PortEnd:       4100,
			MaxProcesses:  4,
			OpencodeBin:   "opencode",
			StateDir:      t.TempDir(),
			SessionRoot:   "/tmp/opencode-session",
			ConfigDir:     configDir,
		},
		state.NewFileStore(t.TempDir()),
		starter,
		noopSignaler{},
		health.Checker{},
	)
	supervisor := NewSupervisor(supervisorTestConfig("ws://127.0.0.1:1"), manager)
	sessionPath := "/tmp/opencode-session/users/usr_1234567890abcdef"

	result, err := supervisor.dispatchProcessCommand(context.Background(), Message{
		Command:     "start",
		Port:        4096,
		SessionPath: sessionPath,
		TraceID:     "trace_1234567890abcdef",
	}, time.Second)
	if err != nil {
		t.Fatalf("dispatchProcessCommand returned error: %v", err)
	}

	if result.SessionPath != sessionPath {
		t.Fatalf("expected result session path %q, got %q", sessionPath, result.SessionPath)
	}
	if len(starter.specs) != 1 {
		t.Fatalf("expected one start spec, got %d", len(starter.specs))
	}
	if starter.specs[0].SessionPath != sessionPath {
		t.Fatalf("expected start spec session path %q, got %q", sessionPath, starter.specs[0].SessionPath)
	}
}

func TestSupervisorConnectsSeedWebSocketWithoutHTTPDiscovery(t *testing.T) {
	registered := make(chan Message, 1)
	seedURL, cleanup := websocketServer(t, func(ctx context.Context, connection *websocket.Conn) {
		_, payload, err := connection.Read(ctx)
		if err != nil {
			return
		}
		var message Message
		if err := json.Unmarshal(payload, &message); err != nil {
			return
		}
		if message.Type == messageTypeRegister {
			registered <- message
			_ = writeTestMessage(ctx, connection, Message{
				Type:             messageTypeRegistered,
				ProtocolVersion:  protocolVersion,
				BackendProcessID: "bjp_seed_1234567890",
				TraceID:          message.TraceID,
			})
		}
		<-ctx.Done()
	})
	defer cleanup()
	cfg := supervisorTestConfig(seedURL)
	supervisor := NewSupervisor(cfg, testProcessManager())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		_ = supervisor.Run(ctx)
	}()

	message := receiveMessage(t, registered, time.Second)

	if message.ManagerID != "mgr_ctr_01_opencode_manager" {
		t.Fatalf("expected seed WebSocket register message, got %#v", message)
	}
}

func TestSupervisorIgnoresBackendListAndDoesNotConnectOtherBackends(t *testing.T) {
	secondConnected := make(chan Message, 1)
	secondURL, secondCleanup := websocketServer(t, func(ctx context.Context, connection *websocket.Conn) {
		_, payload, err := connection.Read(ctx)
		if err != nil {
			return
		}
		var message Message
		if err := json.Unmarshal(payload, &message); err == nil && message.Type == messageTypeRegister {
			secondConnected <- message
		}
		<-ctx.Done()
	})
	defer secondCleanup()

	listRequested := make(chan Message, 1)
	seedURL, seedCleanup := websocketServer(t, func(ctx context.Context, connection *websocket.Conn) {
		_, payload, err := connection.Read(ctx)
		if err != nil {
			return
		}
		var register Message
		if err := json.Unmarshal(payload, &register); err == nil && register.Type == messageTypeRegister {
			_ = writeTestMessage(ctx, connection, Message{
				Type:             messageTypeRegistered,
				ProtocolVersion:  protocolVersion,
				BackendProcessID: "bjp_seed_1234567890",
				TraceID:          register.TraceID,
			})
			_ = writeTestMessage(ctx, connection, Message{
				Type:            messageTypeBackendListResponse,
				ProtocolVersion: protocolVersion,
				TraceID:         register.TraceID,
				BackendEndpoints: []BackendEndpoint{{
					BackendProcessID: "bjp_second_12345678",
					LinuxServerID:    "10.8.0.12",
					ListenURL:        "http://10.8.0.12:8081",
					WebSocketURL:     secondURL,
					LastHeartbeatAt:  time.Now().UTC(),
				}},
			})
		}
		for {
			_, payload, err := connection.Read(ctx)
			if err != nil {
				return
			}
			var message Message
			if err := json.Unmarshal(payload, &message); err != nil {
				continue
			}
			if message.Type == messageTypeBackendListRequest {
				listRequested <- message
			}
		}
	})
	defer seedCleanup()

	cfg := supervisorTestConfig(seedURL)
	cfg.HeartbeatInterval = 20 * time.Millisecond
	supervisor := NewSupervisor(cfg, testProcessManager())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		_ = supervisor.Run(ctx)
	}()

	assertNoMessage(t, listRequested, 120*time.Millisecond, "manager must not request backend list")
	assertNoMessage(t, secondConnected, 120*time.Millisecond, "manager must not connect backend list endpoints")
}

func TestSupervisorSendsManagerHeartbeatThroughOneConnectedSocket(t *testing.T) {
	heartbeats := make(chan Message, 8)
	seedURL, cleanup := websocketServer(t, func(ctx context.Context, connection *websocket.Conn) {
		_, payload, err := connection.Read(ctx)
		if err != nil {
			return
		}
		var register Message
		if err := json.Unmarshal(payload, &register); err == nil && register.Type == messageTypeRegister {
			_ = writeTestMessage(ctx, connection, Message{
				Type:             messageTypeRegistered,
				ProtocolVersion:  protocolVersion,
				BackendProcessID: "bjp_seed_1234567890",
				TraceID:          register.TraceID,
			})
		}
		for {
			_, payload, err := connection.Read(ctx)
			if err != nil {
				return
			}
			var message Message
			if err := json.Unmarshal(payload, &message); err != nil {
				continue
			}
			if message.Type == messageTypeManagerHeartbeat {
				heartbeats <- message
			}
			if message.Type == messageTypeHeartbeat {
				t.Errorf("legacy per-connection heartbeat must not be sent")
			}
		}
	})
	defer cleanup()
	cfg := supervisorTestConfig(seedURL)
	cfg.HeartbeatInterval = 20 * time.Millisecond
	supervisor := NewSupervisor(cfg, testProcessManager())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		_ = supervisor.Run(ctx)
	}()

	heartbeat := receiveMessage(t, heartbeats, time.Second)

	if len(heartbeat.ConnectedBackendProcessIDs) != 1 || heartbeat.ConnectedBackendProcessIDs[0] != "bjp_seed_1234567890" {
		t.Fatalf("expected heartbeat to report connected backend id, got %#v", heartbeat.ConnectedBackendProcessIDs)
	}
}

func TestSupervisorHeartbeatIncludesResourceMetricsAndManagedProcesses(t *testing.T) {
	heartbeats := make(chan Message, 8)
	seedURL, cleanup := websocketServer(t, func(ctx context.Context, connection *websocket.Conn) {
		_, payload, err := connection.Read(ctx)
		if err != nil {
			return
		}
		var register Message
		if err := json.Unmarshal(payload, &register); err == nil && register.Type == messageTypeRegister {
			_ = writeTestMessage(ctx, connection, Message{
				Type:             messageTypeRegistered,
				ProtocolVersion:  protocolVersion,
				BackendProcessID: "bjp_seed_1234567890",
				TraceID:          register.TraceID,
			})
		}
		for {
			_, payload, err := connection.Read(ctx)
			if err != nil {
				return
			}
			var message Message
			if err := json.Unmarshal(payload, &message); err != nil {
				continue
			}
			if message.Type == messageTypeManagerHeartbeat {
				heartbeats <- message
			}
		}
	})
	defer cleanup()
	cfg := supervisorTestConfig(seedURL)
	cfg.HeartbeatInterval = 20 * time.Millisecond
	manager := process.NewManager(cfg.Config, staticStore{records: []state.ProcessRecord{{
		Port:         4096,
		PID:          12345,
		BaseURL:      "http://10.8.0.12:4096",
		SessionPath:  "/data/opencode/session/4096",
		ConfigPath:   "/data/opencode/.config/opencode/",
		StartedAt:    time.Date(2026, 6, 24, 8, 0, 0, 0, time.UTC),
		StartCommand: "XDG_DATA_HOME=/data/opencode/session/4096 OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/ opencode serve --hostname 0.0.0.0 --port 4096 --print-logs",
		TraceID:      "trace_process",
	}}}, nil, nil, health.Checker{ProcessAlive: func(pid int) bool { return true }})
	supervisor := NewSupervisor(cfg, manager)
	supervisor.metrics = staticMetricsCollector{sample: RuntimeMetricsSample{
		CPUUsagePercent:         float64Ptr(12.5),
		MemoryMaxBytes:          int64Ptr(1024),
		MemoryUsedBytes:         int64Ptr(512),
		MemoryUsagePercent:      float64Ptr(50),
		DiskReadBytesPerSecond:  float64Ptr(128),
		DiskWriteBytesPerSecond: float64Ptr(256),
	}}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		_ = supervisor.Run(ctx)
	}()

	heartbeat := receiveMessage(t, heartbeats, time.Second)

	if heartbeat.CPUUsagePercent == nil || *heartbeat.CPUUsagePercent != 12.5 {
		t.Fatalf("expected heartbeat cpu metric, got %#v", heartbeat.CPUUsagePercent)
	}
	if heartbeat.MemoryMaxBytes == nil || *heartbeat.MemoryMaxBytes != 1024 {
		t.Fatalf("expected heartbeat memory max, got %#v", heartbeat.MemoryMaxBytes)
	}
	if len(heartbeat.ManagedProcesses) != 1 || heartbeat.ManagedProcesses[0].Port != 4096 || heartbeat.ManagedProcesses[0].PID != 12345 {
		t.Fatalf("expected managed process details, got %#v", heartbeat.ManagedProcesses)
	}
	if heartbeat.ManagedProcesses[0].StartCommand == "" {
		t.Fatalf("expected managed process start command")
	}
}

func TestSupervisorAppliesConfigUpdateAndReportsLiveMaxInHeartbeat(t *testing.T) {
	heartbeats := make(chan Message, 8)
	configRequests := make(chan Message, 2)
	seedURL, cleanup := websocketServer(t, func(ctx context.Context, connection *websocket.Conn) {
		_, payload, err := connection.Read(ctx)
		if err != nil {
			return
		}
		var register Message
		if err := json.Unmarshal(payload, &register); err == nil && register.Type == messageTypeRegister {
			_ = writeTestMessage(ctx, connection, Message{
				Type:             messageTypeRegistered,
				ProtocolVersion:  protocolVersion,
				BackendProcessID: "bjp_seed_1234567890",
				TraceID:          register.TraceID,
			})
		}
		for {
			_, payload, err := connection.Read(ctx)
			if err != nil {
				return
			}
			var message Message
			if err := json.Unmarshal(payload, &message); err != nil {
				continue
			}
			if message.Type == messageTypeConfigRequest {
				configRequests <- message
				// manager 主动拉取配置后再下发 configUpdate，超过端口池时应裁剪并立即通过心跳回报生效值。
				_ = writeTestMessage(ctx, connection, Message{
					Type:            messageTypeConfigUpdate,
					ProtocolVersion: protocolVersion,
					TraceID:         message.TraceID,
					MaxProcesses:    9,
					SessionRoot:     "/data/.testagent/agent-opencode/.session/",
					ConfigDir:       "/data/.testagent/agent-opencode/.config/opencode/",
				})
			}
			if message.Type == messageTypeManagerHeartbeat {
				heartbeats <- message
			}
		}
	})
	defer cleanup()
	cfg := supervisorTestConfig(seedURL)
	cfg.HeartbeatInterval = time.Hour
	manager := testProcessManager() // 初始 MaxProcesses = 4
	supervisor := NewSupervisor(cfg, manager)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		_ = supervisor.Run(ctx)
	}()

	configRequest := receiveMessage(t, configRequests, time.Second)
	if configRequest.Type != messageTypeConfigRequest {
		t.Fatalf("expected config request after registration, got %#v", configRequest)
	}
	heartbeat := receiveMessage(t, heartbeats, time.Second)
	if heartbeat.MaxProcesses != 5 {
		t.Fatalf("expected immediate heartbeat to report clamped live maxProcesses 5, got %d", heartbeat.MaxProcesses)
	}
	if manager.MaxProcesses() != 5 {
		t.Fatalf("expected manager runtime max 5, got %d", manager.MaxProcesses())
	}
}

func TestSupervisorAppliesMaxOnlyConfigUpdateAndReportsHeartbeat(t *testing.T) {
	heartbeats := make(chan Message, 8)
	seedURL, cleanup := websocketServer(t, func(ctx context.Context, connection *websocket.Conn) {
		_, payload, err := connection.Read(ctx)
		if err != nil {
			return
		}
		var register Message
		if err := json.Unmarshal(payload, &register); err == nil && register.Type == messageTypeRegister {
			_ = writeTestMessage(ctx, connection, Message{
				Type:             messageTypeRegistered,
				ProtocolVersion:  protocolVersion,
				BackendProcessID: "bjp_seed_1234567890",
				TraceID:          register.TraceID,
			})
		}
		maxOnlySent := false
		for {
			_, payload, err := connection.Read(ctx)
			if err != nil {
				return
			}
			var message Message
			if err := json.Unmarshal(payload, &message); err != nil {
				continue
			}
			if message.Type == messageTypeConfigRequest {
				_ = writeTestMessage(ctx, connection, Message{
					Type:            messageTypeConfigUpdate,
					ProtocolVersion: protocolVersion,
					TraceID:         message.TraceID,
					MaxProcesses:    5,
					SessionRoot:     "/data/.testagent/agent-opencode/.session/",
					ConfigDir:       "/data/.testagent/agent-opencode/.config/opencode/",
				})
			}
			if message.Type == messageTypeManagerHeartbeat {
				heartbeats <- message
				if !maxOnlySent {
					maxOnlySent = true
					_ = writeTestMessage(ctx, connection, Message{
						Type:            messageTypeConfigUpdate,
						ProtocolVersion: protocolVersion,
						TraceID:         "trace_max_only",
						MaxProcesses:    2,
					})
				}
			}
		}
	})
	defer cleanup()
	cfg := supervisorTestConfig(seedURL)
	cfg.HeartbeatInterval = time.Hour
	manager := testProcessManager()
	supervisor := NewSupervisor(cfg, manager)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		_ = supervisor.Run(ctx)
	}()

	initialHeartbeat := receiveMessage(t, heartbeats, time.Second)
	if initialHeartbeat.MaxProcesses != 5 {
		t.Fatalf("expected full config heartbeat maxProcesses 5, got %d", initialHeartbeat.MaxProcesses)
	}
	maxOnlyHeartbeat := receiveMessage(t, heartbeats, time.Second)
	if maxOnlyHeartbeat.MaxProcesses != 2 {
		t.Fatalf("expected max-only config heartbeat maxProcesses 2, got %d", maxOnlyHeartbeat.MaxProcesses)
	}
	if manager.MaxProcesses() != 2 {
		t.Fatalf("expected manager runtime max 2 after max-only update, got %d", manager.MaxProcesses())
	}
}

func TestSupervisorCommandResultIncludesPublicConfigErrorCode(t *testing.T) {
	cfg := supervisorTestConfig("")
	cfg.Config.StateDir = t.TempDir()
	cfg.Config.SessionRoot = t.TempDir() + "/sessions"
	cfg.Config.ConfigDir = t.TempDir() + "/missing-opencode-config"
	manager := process.NewManager(cfg.Config, state.NewFileStore(t.TempDir()), nil, fakeSupervisorSignaler{}, health.Checker{})
	supervisor := NewSupervisor(cfg, manager)

	result := supervisor.executeCommand(context.Background(), Message{
		Type:          messageTypeCommand,
		CommandID:     "mcmd_start_1234567890",
		Command:       "start",
		Port:          4096,
		TimeoutMillis: int64(time.Second / time.Millisecond),
		TraceID:       "trace_start_1234567890",
	})

	if result.Status != string(process.StatusFailed) {
		t.Fatalf("expected failed command result, got %#v", result)
	}
	if result.ErrorCode != "OPENCODE_UNAVAILABLE" {
		t.Fatalf("expected OPENCODE_UNAVAILABLE errorCode, got %q", result.ErrorCode)
	}
	wantMessage := "服务器" + cfg.Config.LinuxServerID + "，公共 opencode 配置目录" + cfg.Config.ConfigDir + "尚未初始化。请联系超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化后重试。"
	if result.Message != wantMessage {
		t.Fatalf("unexpected command result message %q", result.Message)
	}
}

func TestSupervisorSendsHeartbeatImmediatelyAfterStopCommand(t *testing.T) {
	commandResults := make(chan Message, 2)
	heartbeats := make(chan Message, 4)
	seedURL, cleanup := websocketServer(t, func(ctx context.Context, connection *websocket.Conn) {
		_, payload, err := connection.Read(ctx)
		if err != nil {
			return
		}
		var register Message
		if err := json.Unmarshal(payload, &register); err == nil && register.Type == messageTypeRegister {
			_ = writeTestMessage(ctx, connection, Message{
				Type:             messageTypeRegistered,
				ProtocolVersion:  protocolVersion,
				BackendProcessID: "bjp_seed_1234567890",
				TraceID:          register.TraceID,
			})
			_ = writeTestMessage(ctx, connection, Message{
				Type:            messageTypeCommand,
				ProtocolVersion: protocolVersion,
				CommandID:       "mcmd_stop_1234567890",
				Command:         "stop",
				Port:            4096,
				TimeoutMillis:   1,
				TraceID:         "trace_stop_1234567890",
			})
		}
		for {
			_, payload, err := connection.Read(ctx)
			if err != nil {
				return
			}
			var message Message
			if err := json.Unmarshal(payload, &message); err != nil {
				continue
			}
			if message.Type == messageTypeCommandResult {
				commandResults <- message
			}
			if message.Type == messageTypeManagerHeartbeat {
				heartbeats <- message
			}
		}
	})
	defer cleanup()

	cfg := supervisorTestConfig(seedURL)
	cfg.HeartbeatInterval = time.Hour
	store := state.NewFileStore(t.TempDir())
	_ = store.Save(state.ProcessRecord{
		Port:      4096,
		PID:       12345,
		BaseURL:   "http://10.8.0.12:4096",
		StartedAt: time.Now().UTC(),
		TraceID:   "trace_process",
	})
	manager := process.NewManager(cfg.Config, store, nil, fakeSupervisorSignaler{}, health.Checker{
		ProcessAlive: func(pid int) bool { return true },
	})
	supervisor := NewSupervisor(cfg, manager)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		_ = supervisor.Run(ctx)
	}()

	result := receiveMessage(t, commandResults, time.Second)
	if result.Status != string(process.StatusStopped) {
		t.Fatalf("expected stop command result, got %#v", result)
	}
	heartbeat := receiveMessage(t, heartbeats, time.Second)
	if heartbeat.Type != messageTypeManagerHeartbeat || heartbeat.CurrentProcesses != 0 || len(heartbeat.ManagedProcesses) != 0 {
		t.Fatalf("expected immediate empty heartbeat after stop, got %#v", heartbeat)
	}
}

func supervisorTestConfig(webSocketURL string) config.ControlConfig {
	return config.ControlConfig{
		Config: config.Config{
			ContainerID:   "ctr_01",
			LinuxServerID: "test-agent-backend-10-8-0-12",
			ServerHost:    "10.8.0.12",
			PortStart:     4096,
			PortEnd:       4100,
			MaxProcesses:  4,
			OpencodeBin:   "opencode",
			StateDir:      "/tmp/opencode-manager",
			SessionRoot:   "/tmp/opencode-session",
			ConfigDir:     "/tmp/opencode-config",
		},
		ManagerID:           "mgr_ctr_01_opencode_manager",
		BackendWebSocketURL: webSocketURL,
		Token:               "manager-secret",
		HeartbeatInterval:   time.Hour,
		ReconnectInterval:   10 * time.Millisecond,
	}
}

func websocketServer(t *testing.T, handler func(context.Context, *websocket.Conn)) (string, func()) {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		connection, err := websocket.Accept(writer, request, nil)
		if err != nil {
			return
		}
		defer connection.Close(websocket.StatusNormalClosure, "test done")
		handler(request.Context(), connection)
	}))
	return "ws" + strings.TrimPrefix(server.URL, "http"), server.Close
}

func writeTestMessage(ctx context.Context, connection *websocket.Conn, message Message) error {
	payload, err := json.Marshal(message)
	if err != nil {
		return err
	}
	return connection.Write(ctx, websocket.MessageText, payload)
}

func receiveMessage(t *testing.T, messages <-chan Message, timeout time.Duration) Message {
	t.Helper()
	select {
	case message := <-messages:
		return message
	case <-time.After(timeout):
		t.Fatal("timed out waiting for message")
	}
	return Message{}
}

func assertNoMessage(t *testing.T, messages <-chan Message, timeout time.Duration, failure string) {
	t.Helper()
	select {
	case message := <-messages:
		t.Fatalf("%s, got %#v", failure, message)
	case <-time.After(timeout):
	}
}

func testProcessManager() *process.Manager {
	return process.NewManager(
		config.Config{
			ContainerID:   "ctr_01",
			LinuxServerID: "test-agent-backend-10-8-0-12",
			ServerHost:    "10.8.0.12",
			PortStart:     4096,
			PortEnd:       4100,
			MaxProcesses:  4,
			OpencodeBin:   "opencode",
			StateDir:      "/tmp/opencode-manager",
			SessionRoot:   "/tmp/opencode-session",
			ConfigDir:     "/tmp/opencode-config",
		},
		emptyStore{},
		nil,
		nil,
		health.Checker{},
	)
}

type emptyStore struct{}

func (emptyStore) Create(state.ProcessRecord) error { return nil }
func (emptyStore) Save(state.ProcessRecord) error   { return nil }
func (emptyStore) Get(int) (state.ProcessRecord, bool, error) {
	return state.ProcessRecord{}, false, nil
}
func (emptyStore) List() ([]state.ProcessRecord, error) { return nil, nil }
func (emptyStore) Delete(int) error                     { return nil }

type staticStore struct {
	records []state.ProcessRecord
}

func (s staticStore) Create(state.ProcessRecord) error { return nil }
func (s staticStore) Save(state.ProcessRecord) error   { return nil }
func (s staticStore) Get(int) (state.ProcessRecord, bool, error) {
	return state.ProcessRecord{}, false, nil
}
func (s staticStore) List() ([]state.ProcessRecord, error) { return s.records, nil }
func (s staticStore) Delete(int) error                     { return nil }

type staticMetricsCollector struct {
	sample RuntimeMetricsSample
}

func (s staticMetricsCollector) Sample() RuntimeMetricsSample {
	return s.sample
}

type capturingStarter struct {
	pid   int
	specs []process.StartSpec
}

func (c *capturingStarter) Start(_ context.Context, spec process.StartSpec) (int, error) {
	c.specs = append(c.specs, spec)
	return c.pid, nil
}

type noopSignaler struct{}

func (noopSignaler) Terminate(int) error { return nil }
func (noopSignaler) Kill(int) error      { return nil }

func float64Ptr(value float64) *float64 {
	return &value
}

func int64Ptr(value int64) *int64 {
	return &value
}

type fakeSupervisorSignaler struct{}

func (fakeSupervisorSignaler) Terminate(int) error { return nil }
func (fakeSupervisorSignaler) Kill(int) error      { return nil }
