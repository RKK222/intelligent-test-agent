package control

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/icbc/test-agent/opencode-manager/internal/config"
	"github.com/icbc/test-agent/opencode-manager/internal/health"
	"github.com/icbc/test-agent/opencode-manager/internal/process"
	"github.com/icbc/test-agent/opencode-manager/internal/state"
	"nhooyr.io/websocket"
)

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

	if message.ManagerID != "mgr_1234567890abcdef" {
		t.Fatalf("expected seed WebSocket register message, got %#v", message)
	}
}

func TestSupervisorAsksConnectedSocketForBackendListAndConnectsMissingBackend(t *testing.T) {
	secondRegistered := make(chan Message, 1)
	secondURL, secondCleanup := websocketServer(t, func(ctx context.Context, connection *websocket.Conn) {
		_, payload, err := connection.Read(ctx)
		if err != nil {
			return
		}
		var message Message
		if err := json.Unmarshal(payload, &message); err == nil && message.Type == messageTypeRegister {
			secondRegistered <- message
			_ = writeTestMessage(ctx, connection, Message{
				Type:             messageTypeRegistered,
				ProtocolVersion:  protocolVersion,
				BackendProcessID: "bjp_second_12345678",
				TraceID:          message.TraceID,
			})
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
				_ = writeTestMessage(ctx, connection, Message{
					Type:            messageTypeBackendListResponse,
					ProtocolVersion: protocolVersion,
					TraceID:         message.TraceID,
					BackendEndpoints: []BackendEndpoint{{
						BackendProcessID: "bjp_second_12345678",
						LinuxServerID:    "10.8.0.12",
						ListenURL:        "http://10.8.0.12:8081",
						WebSocketURL:     secondURL,
						LastHeartbeatAt:  time.Now().UTC(),
					}},
				})
			}
		}
	})
	defer seedCleanup()

	cfg := supervisorTestConfig(seedURL)
	cfg.DiscoveryInterval = 20 * time.Millisecond
	supervisor := NewSupervisor(cfg, testProcessManager())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		_ = supervisor.Run(ctx)
	}()

	_ = receiveMessage(t, listRequested, time.Second)
	second := receiveMessage(t, secondRegistered, time.Second)

	if second.Type != messageTypeRegister {
		t.Fatalf("expected supervisor to register with missing backend, got %#v", second)
	}
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
		Port:        4096,
		PID:         12345,
		BaseURL:     "http://10.8.0.12:4096",
		SessionPath: "/data/opencode/session/4096",
		ConfigPath:  "/data/opencode/.config/opencode/",
		StartedAt:   time.Date(2026, 6, 24, 8, 0, 0, 0, time.UTC),
		TraceID:     "trace_process",
	}}}, nil, nil, health.Checker{})
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
}

func TestSupervisorAppliesConfigUpdateAndReportsLiveMaxInHeartbeat(t *testing.T) {
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
			// 注册后立即下发 configUpdate，把最大进程数从 cfg 的 4 调到 2。
			_ = writeTestMessage(ctx, connection, Message{
				Type:            messageTypeConfigUpdate,
				ProtocolVersion: protocolVersion,
				TraceID:         register.TraceID,
				MaxProcesses:    2,
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
	manager := testProcessManager() // 初始 MaxProcesses = 4
	supervisor := NewSupervisor(cfg, manager)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		_ = supervisor.Run(ctx)
	}()

	heartbeat := receiveMessage(t, heartbeats, time.Second)
	if heartbeat.MaxProcesses != 2 {
		t.Fatalf("expected heartbeat to report live maxProcesses 2, got %d", heartbeat.MaxProcesses)
	}
	if manager.MaxProcesses() != 2 {
		t.Fatalf("expected manager runtime max 2, got %d", manager.MaxProcesses())
	}
}

func supervisorTestConfig(webSocketURL string) config.ControlConfig {
	return config.ControlConfig{
		Config: config.Config{
			ContainerID:   "ctr_01",
			LinuxServerID: "10.8.0.12",
			PortStart:     4096,
			PortEnd:       4100,
			MaxProcesses:  4,
			OpencodeBin:   "opencode",
			StateDir:      "/tmp/opencode-manager",
			SessionRoot:   "/tmp/opencode-session",
			ConfigDir:     "/tmp/opencode-config",
		},
		ManagerID:           "mgr_1234567890abcdef",
		BackendWebSocketURL: webSocketURL,
		Token:               "manager-secret",
		DiscoveryInterval:   time.Hour,
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

func testProcessManager() *process.Manager {
	return process.NewManager(
		config.Config{
			ContainerID:   "ctr_01",
			LinuxServerID: "10.8.0.12",
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

func float64Ptr(value float64) *float64 {
	return &value
}

func int64Ptr(value int64) *int64 {
	return &value
}
