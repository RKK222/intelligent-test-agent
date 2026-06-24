package control

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/icbc/test-agent/opencode-manager/internal/config"
	"github.com/icbc/test-agent/opencode-manager/internal/process"
	"nhooyr.io/websocket"
)

// Supervisor 周期发现后端实例，并确保 manager 与每个实例保持 WebSocket 长连接。
type Supervisor struct {
	cfg       config.ControlConfig
	manager   *process.Manager
	discovery *DiscoveryClient

	mu          sync.Mutex
	writeMu     sync.Mutex
	connections map[string]context.CancelFunc
}

// NewSupervisor 创建 WebSocket 控制面 supervisor。
func NewSupervisor(cfg config.ControlConfig, manager *process.Manager, discovery *DiscoveryClient) *Supervisor {
	if discovery == nil {
		discovery = NewDiscoveryClient(cfg.BackendDiscoveryURL, cfg.Token, http.DefaultClient)
	}
	return &Supervisor{
		cfg:         cfg,
		manager:     manager,
		discovery:   discovery,
		connections: map[string]context.CancelFunc{},
	}
}

// Run 阻塞运行 discovery/reconnect 循环，直到 ctx 取消。
func (s *Supervisor) Run(ctx context.Context) error {
	if err := s.discoverOnce(ctx); err != nil {
		log.Printf("manager discovery failed: %v", err)
	}
	ticker := time.NewTicker(s.cfg.DiscoveryInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			s.stopAll()
			return ctx.Err()
		case <-ticker.C:
			if err := s.discoverOnce(ctx); err != nil {
				log.Printf("manager discovery failed: %v", err)
			}
		}
	}
}

func (s *Supervisor) discoverOnce(ctx context.Context) error {
	backends, err := s.discovery.Discover(ctx)
	if err != nil {
		return err
	}
	for _, backend := range backends {
		if backend.BackendProcessID == "" || backend.WebSocketURL == "" {
			continue
		}
		s.ensureConnection(ctx, backend)
	}
	return nil
}

func (s *Supervisor) ensureConnection(parent context.Context, backend BackendEndpoint) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.connections[backend.BackendProcessID]; ok {
		return
	}
	ctx, cancel := context.WithCancel(parent)
	s.connections[backend.BackendProcessID] = cancel
	go func() {
		defer func() {
			s.mu.Lock()
			delete(s.connections, backend.BackendProcessID)
			s.mu.Unlock()
		}()
		for {
			if err := s.runConnection(ctx, backend); err != nil && ctx.Err() == nil {
				log.Printf("manager websocket disconnected from backend %s: %v", backend.BackendProcessID, err)
			}
			select {
			case <-ctx.Done():
				return
			case <-time.After(s.cfg.ReconnectInterval):
			}
		}
	}()
}

func (s *Supervisor) runConnection(ctx context.Context, backend BackendEndpoint) error {
	header := http.Header{}
	header.Set("Authorization", "Bearer "+s.cfg.Token)
	connection, _, err := websocket.Dial(ctx, backend.WebSocketURL, &websocket.DialOptions{HTTPHeader: header})
	if err != nil {
		return err
	}
	defer connection.Close(websocket.StatusNormalClosure, "manager stopped")

	if err := s.writeJSON(ctx, connection, s.topologyMessage(messageTypeRegister)); err != nil {
		return err
	}

	errCh := make(chan error, 1)
	go func() {
		errCh <- s.readLoop(ctx, connection)
	}()

	heartbeat := time.NewTicker(s.cfg.HeartbeatInterval)
	defer heartbeat.Stop()
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case err := <-errCh:
			return err
		case <-heartbeat.C:
			if err := s.writeJSON(ctx, connection, s.topologyMessage(messageTypeHeartbeat)); err != nil {
				return err
			}
		}
	}
}

func (s *Supervisor) readLoop(ctx context.Context, connection *websocket.Conn) error {
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
			continue
		case messageTypeCommand:
			result := s.executeCommand(ctx, message)
			if err := s.writeJSON(ctx, connection, result); err != nil {
				return err
			}
		case messageTypeError:
			log.Printf("manager websocket received backend error: %s", message.ErrorCode)
		default:
			log.Printf("manager websocket ignored message type: %s", message.Type)
		}
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
	records, err := s.manager.List(traceID())
	currentProcesses := 0
	if err == nil {
		currentProcesses = len(records.Records)
	}
	return Message{
		Type:             messageType,
		ProtocolVersion:  protocolVersion,
		TraceID:          traceID(),
		ManagerID:        s.cfg.ManagerID,
		ContainerID:      s.cfg.ContainerID,
		LinuxServerID:    s.cfg.LinuxServerID,
		ContainerName:    s.cfg.ContainerID,
		PortStart:        s.cfg.PortStart,
		PortEnd:          s.cfg.PortEnd,
		MaxProcesses:     s.cfg.MaxProcesses,
		CurrentProcesses: currentProcesses,
		Capabilities: map[string]any{
			"commands": []string{"start", "health", "stop", "restart"},
		},
	}
}

func (s *Supervisor) writeJSON(ctx context.Context, connection *websocket.Conn, message Message) error {
	payload, err := json.Marshal(message)
	if err != nil {
		return err
	}
	s.writeMu.Lock()
	defer s.writeMu.Unlock()
	return connection.Write(ctx, websocket.MessageText, payload)
}

func (s *Supervisor) stopAll() {
	s.mu.Lock()
	defer s.mu.Unlock()
	for _, cancel := range s.connections {
		cancel()
	}
	s.connections = map[string]context.CancelFunc{}
}
