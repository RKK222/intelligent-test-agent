package control

import "time"

const (
	protocolVersion = "opencode-manager.v1"

	messageTypeRegister      = "register"
	messageTypeRegistered    = "registered"
	messageTypeHeartbeat     = "heartbeat"
	messageTypeCommand       = "command"
	messageTypeCommandResult = "commandResult"
	messageTypeError         = "error"
)

// Message 是 manager 与后端 WebSocket 控制面的稳定 JSON 帧。
type Message struct {
	Type             string         `json:"type"`
	ProtocolVersion  string         `json:"protocolVersion"`
	TraceID          string         `json:"traceId,omitempty"`
	ManagerID        string         `json:"managerId,omitempty"`
	ContainerID      string         `json:"containerId,omitempty"`
	LinuxServerID    string         `json:"linuxServerId,omitempty"`
	ContainerName    string         `json:"containerName,omitempty"`
	PortStart        int            `json:"portStart,omitempty"`
	PortEnd          int            `json:"portEnd,omitempty"`
	MaxProcesses     int            `json:"maxProcesses,omitempty"`
	CurrentProcesses int            `json:"currentProcesses,omitempty"`
	Capabilities     map[string]any `json:"capabilities,omitempty"`
	BackendProcessID string         `json:"backendProcessId,omitempty"`
	CommandID        string         `json:"commandId,omitempty"`
	Command          string         `json:"command,omitempty"`
	Port             int            `json:"port,omitempty"`
	TimeoutMillis    int64          `json:"timeoutMillis,omitempty"`
	Status           string         `json:"status,omitempty"`
	PID              int            `json:"pid,omitempty"`
	BaseURL          string         `json:"baseUrl,omitempty"`
	SessionPath      string         `json:"sessionPath,omitempty"`
	ConfigPath       string         `json:"configPath,omitempty"`
	Healthy          *bool          `json:"healthy,omitempty"`
	Message          string         `json:"message,omitempty"`
	ErrorCode        string         `json:"errorCode,omitempty"`
}

func traceID() string {
	now := time.Now().UTC()
	return "trace_" + now.Format("20060102150405") + "_" + now.Format("000000000")
}
