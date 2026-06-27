package control

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math/rand"
	"net/http"
	"sync"
	"time"

	"github.com/icbc/test-agent/opencode-manager/internal/config"
	"github.com/icbc/test-agent/opencode-manager/internal/process"
	"nhooyr.io/websocket"
)

// Supervisor 通过 WebSocket 与 Java 后端保持控制面连接，不再通过 HTTP discovery 与 Java 交互。
type Supervisor struct {
	cfg     config.ControlConfig
	manager *process.Manager
	metrics RuntimeMetricsCollector

	mu          sync.Mutex
	connections map[string]*connectionState
}

type connectionState struct {
	endpoint         BackendEndpoint
	cancel           context.CancelFunc
	connection       *websocket.Conn
	backendProcessID string
	writeMu          sync.Mutex
}

// NewSupervisor 创建 WebSocket 控制面 supervisor。
func NewSupervisor(cfg config.ControlConfig, manager *process.Manager) *Supervisor {
	return &Supervisor{
		cfg:         cfg,
		manager:     manager,
		metrics:     NewRuntimeMetricsCollector(),
		connections: map[string]*connectionState{},
	}
}

// Run 阻塞运行 WebSocket seed、心跳和后端列表补连循环，直到 ctx 取消。
func (s *Supervisor) Run(ctx context.Context) error {
	s.ensureConnection(ctx, BackendEndpoint{
		BackendProcessID: "seed",
		LinuxServerID:    s.cfg.LinuxServerID,
		WebSocketURL:     s.cfg.BackendWebSocketURL,
	})

	backendListTicker := time.NewTicker(s.cfg.DiscoveryInterval)
	defer backendListTicker.Stop()
	heartbeatTicker := time.NewTicker(s.cfg.HeartbeatInterval)
	defer heartbeatTicker.Stop()

	for {
		select {
		case <-ctx.Done():
			s.stopAll()
			return ctx.Err()
		case <-backendListTicker.C:
			s.requestBackendList(ctx)
		case <-heartbeatTicker.C:
			s.sendManagerHeartbeat(ctx)
		}
	}
}

func (s *Supervisor) ensureConnection(parent context.Context, backend BackendEndpoint) {
	if backend.WebSocketURL == "" {
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.connections[backend.WebSocketURL]; ok {
		return
	}
	ctx, cancel := context.WithCancel(parent)
	state := &connectionState{endpoint: backend, cancel: cancel}
	s.connections[backend.WebSocketURL] = state
	go func() {
		for {
			if err := s.runConnection(ctx, state); err != nil && ctx.Err() == nil {
				log.Printf("manager websocket disconnected from backend %s: %v", state.endpoint.WebSocketURL, err)
			}
			select {
			case <-ctx.Done():
				return
			case <-time.After(s.cfg.ReconnectInterval):
			}
		}
	}()
}

func (s *Supervisor) runConnection(ctx context.Context, state *connectionState) error {
	header := http.Header{}
	header.Set("Authorization", "Bearer "+s.cfg.Token)
	connection, _, err := websocket.Dial(ctx, state.endpoint.WebSocketURL, &websocket.DialOptions{HTTPHeader: header})
	if err != nil {
		return err
	}
	state.setConnection(connection)
	defer func() {
		state.clearConnection(connection)
		connection.Close(websocket.StatusNormalClosure, "manager stopped")
	}()

	if err := state.writeJSON(ctx, s.topologyMessage(messageTypeRegister)); err != nil {
		return err
	}
	return s.readLoop(ctx, state, connection)
}

func (s *Supervisor) readLoop(ctx context.Context, state *connectionState, connection *websocket.Conn) error {
	for {
		_, payload, err := connection.Read(ctx)
		if err != nil {
			return err
		}
		var message Message
		if err := json.Unmarshal(payload, &message); err != nil {
			return err
		}
		switch message.Type {
		case messageTypeRegistered:
			state.setBackendProcessID(message.BackendProcessID)
		case messageTypeBackendListResponse:
			s.handleBackendList(ctx, message.BackendEndpoints)
		case messageTypeCommand:
			result := s.executeCommand(ctx, message)
			if err := state.writeJSON(ctx, result); err != nil {
				return err
			}
		case messageTypeError:
			log.Printf("manager websocket received backend error: %s", message.ErrorCode)
		case messageTypeConfigUpdate:
			previous := s.manager.MaxProcesses()
			applied, err := s.manager.SetMaxProcesses(message.MaxProcesses)
			if err != nil {
				log.Printf("manager config update rejected: traceId=%s value=%d err=%v", message.TraceID, message.MaxProcesses, err)
				continue
			}
			log.Printf("manager config update applied: traceId=%s maxProcesses %d -> %d (requested %d)", message.TraceID, previous, applied, message.MaxProcesses)
		default:
			log.Printf("manager websocket ignored message type: %s", message.Type)
		}
	}
}

func (s *Supervisor) handleBackendList(ctx context.Context, backends []BackendEndpoint) {
	for _, backend := range backends {
		if backend.WebSocketURL == "" {
			continue
		}
		s.ensureConnection(ctx, backend)
	}
}

func (s *Supervisor) requestBackendList(ctx context.Context) {
	state := s.randomConnectedState()
	if state == nil {
		return
	}
	if err := state.writeJSON(ctx, Message{
		Type:            messageTypeBackendListRequest,
		ProtocolVersion: protocolVersion,
		TraceID:         traceID(),
	}); err != nil {
		log.Printf("manager backend list request failed: %v", err)
	}
}

func (s *Supervisor) sendManagerHeartbeat(ctx context.Context) {
	state := s.randomConnectedState()
	if state == nil {
		return
	}
	if err := state.writeJSON(ctx, s.topologyMessage(messageTypeManagerHeartbeat)); err != nil {
		log.Printf("manager heartbeat failed: %v", err)
	}
}

func (s *Supervisor) executeCommand(ctx context.Context, message Message) Message {
	timeout := time.Duration(message.TimeoutMillis) * time.Millisecond
	if timeout <= 0 {
		timeout = 10 * time.Second
	}
	commandCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	result, err := s.dispatchProcessCommand(commandCtx, message, timeout)
	healthy := result.Status == process.StatusHealthy || result.Status == process.StatusStarted
	status := string(result.Status)
	responseMessage := result.Message
	if err != nil && responseMessage == "" {
		responseMessage = err.Error()
	}
	return Message{
		Type:            messageTypeCommandResult,
		ProtocolVersion: protocolVersion,
		TraceID:         message.TraceID,
		CommandID:       message.CommandID,
		Command:         message.Command,
		Port:            result.Port,
		Status:          status,
		PID:             result.PID,
		BaseURL:         result.BaseURL,
		SessionPath:     result.SessionPath,
		ConfigPath:      result.ConfigPath,
		Healthy:         &healthy,
		Message:         responseMessage,
	}
}

func (s *Supervisor) dispatchProcessCommand(ctx context.Context, message Message, timeout time.Duration) (process.Result, error) {
	switch message.Command {
	case "start":
		return s.manager.Start(ctx, process.StartRequest{Port: message.Port, TraceID: message.TraceID})
	case "health":
		return s.manager.Health(ctx, process.HealthRequest{Port: message.Port, TraceID: message.TraceID})
	case "stop":
		return s.manager.Stop(ctx, process.StopRequest{Port: message.Port, TraceID: message.TraceID, Timeout: timeout})
	case "restart":
		return s.manager.Restart(ctx, process.StopRequest{Port: message.Port, TraceID: message.TraceID, Timeout: timeout})
	default:
		err := fmt.Errorf("unknown command %q", message.Command)
		return process.Result{Status: process.StatusFailed, Port: message.Port, TraceID: message.TraceID, Message: err.Error()}, err
	}
}

func (s *Supervisor) topologyMessage(messageType string) Message {
	id := traceID()
	records, err := s.manager.List(id)
	currentProcesses := 0
	managedProcesses := []ManagedProcess{}
	if err == nil {
		currentProcesses = len(records.Records)
		managedProcesses = make([]ManagedProcess, 0, len(records.Records))
		for _, record := range records.Records {
			managedProcesses = append(managedProcesses, ManagedProcess{
				Port:        record.Port,
				PID:         record.PID,
				BaseURL:     record.BaseURL,
				SessionPath: record.SessionPath,
				ConfigPath:  record.ConfigPath,
				StartedAt:   record.StartedAt,
				TraceID:     record.TraceID,
			})
		}
	}
	metrics := RuntimeMetricsSample{}
	if s.metrics != nil {
		metrics = s.metrics.Sample()
	}
	return Message{
		Type:                    messageType,
		ProtocolVersion:         protocolVersion,
		TraceID:                 id,
		ManagerID:               s.cfg.ManagerID,
		ContainerID:             s.cfg.ContainerID,
		LinuxServerID:           s.cfg.LinuxServerID,
		ContainerName:           s.cfg.ContainerID,
		PortStart:               s.cfg.PortStart,
		PortEnd:                 s.cfg.PortEnd,
		MaxProcesses:            s.manager.MaxProcesses(),
		CurrentProcesses:        currentProcesses,
		MetricsSource:           metrics.MetricsSource,
		CPUUsagePercent:         metrics.CPUUsagePercent,
		MemoryMaxBytes:          metrics.MemoryMaxBytes,
		MemoryUsedBytes:         metrics.MemoryUsedBytes,
		MemoryUsagePercent:      metrics.MemoryUsagePercent,
		DiskReadBytesPerSecond:  metrics.DiskReadBytesPerSecond,
		DiskWriteBytesPerSecond: metrics.DiskWriteBytesPerSecond,
		ManagedProcesses:        managedProcesses,
		Capabilities: map[string]any{
			"commands": []string{"start", "health", "stop", "restart"},
		},
		ConnectedBackendProcessIDs: s.connectedBackendProcessIDs(),
	}
}

func (s *Supervisor) randomConnectedState() *connectionState {
	s.mu.Lock()
	defer s.mu.Unlock()
	states := make([]*connectionState, 0, len(s.connections))
	for _, state := range s.connections {
		if state.isConnected() {
			states = append(states, state)
		}
	}
	if len(states) == 0 {
		return nil
	}
	return states[rand.Intn(len(states))]
}

func (s *Supervisor) connectedBackendProcessIDs() []string {
	s.mu.Lock()
	defer s.mu.Unlock()
	ids := make([]string, 0, len(s.connections))
	for _, state := range s.connections {
		if id := state.getBackendProcessID(); id != "" {
			ids = append(ids, id)
		}
	}
	return ids
}

func (s *Supervisor) stopAll() {
	s.mu.Lock()
	defer s.mu.Unlock()
	for _, state := range s.connections {
		state.cancel()
	}
	s.connections = map[string]*connectionState{}
}

func (s *connectionState) setConnection(connection *websocket.Conn) {
	s.writeMu.Lock()
	defer s.writeMu.Unlock()
	s.connection = connection
}

func (s *connectionState) clearConnection(connection *websocket.Conn) {
	s.writeMu.Lock()
	defer s.writeMu.Unlock()
	if s.connection == connection {
		s.connection = nil
		s.backendProcessID = ""
	}
}

func (s *connectionState) setBackendProcessID(backendProcessID string) {
	s.writeMu.Lock()
	defer s.writeMu.Unlock()
	s.backendProcessID = backendProcessID
}

func (s *connectionState) getBackendProcessID() string {
	s.writeMu.Lock()
	defer s.writeMu.Unlock()
	return s.backendProcessID
}

func (s *connectionState) isConnected() bool {
	s.writeMu.Lock()
	defer s.writeMu.Unlock()
	return s.connection != nil
}

func (s *connectionState) writeJSON(ctx context.Context, message Message) error {
	payload, err := json.Marshal(message)
	if err != nil {
		return err
	}
	s.writeMu.Lock()
	defer s.writeMu.Unlock()
	if s.connection == nil {
		return fmt.Errorf("websocket is not connected")
	}
	return s.connection.Write(ctx, websocket.MessageText, payload)
}
