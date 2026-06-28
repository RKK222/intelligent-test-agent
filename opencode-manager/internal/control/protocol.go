package control

import "time"

const (
	protocolVersion = "opencode-manager.v1"

	messageTypeRegister            = "register"
	messageTypeRegistered          = "registered"
	messageTypeHeartbeat           = "heartbeat"
	messageTypeManagerHeartbeat    = "managerHeartbeat"
	messageTypeBackendListRequest  = "backendListRequest"
	messageTypeBackendListResponse = "backendListResponse"
	messageTypeCommand             = "command"
	messageTypeCommandResult       = "commandResult"
	messageTypeError               = "error"
	messageTypeConfigRequest       = "configRequest"
	messageTypeConfigUpdate        = "configUpdate"
)

// BackendEndpoint 是后端通过 WebSocket 返回的单个可直连 Java 实例。
type BackendEndpoint struct {
	BackendProcessID string    `json:"backendProcessId"`
	LinuxServerID    string    `json:"linuxServerId"`
	ListenURL        string    `json:"listenUrl"`
	WebSocketURL     string    `json:"webSocketUrl"`
	LastHeartbeatAt  time.Time `json:"lastHeartbeatAt"`
}

// ManagedProcess 是 manager 心跳中随 latest snapshot 保存的本地 opencode server 进程明细。
type ManagedProcess struct {
	Port         int       `json:"port"`
	PID          int       `json:"pid"`
	BaseURL      string    `json:"baseUrl,omitempty"`
	SessionPath  string    `json:"sessionPath,omitempty"`
	ConfigPath   string    `json:"configPath,omitempty"`
	StartedAt    time.Time `json:"startedAt,omitempty"`
	StartCommand string    `json:"startCommand,omitempty"`
	TraceID      string    `json:"traceId,omitempty"`
}

// Message 是 manager 与后端 WebSocket 控制面的稳定 JSON 帧。
type Message struct {
	Type                       string            `json:"type"`
	ProtocolVersion            string            `json:"protocolVersion"`
	TraceID                    string            `json:"traceId,omitempty"`
	ManagerID                  string            `json:"managerId,omitempty"`
	ContainerID                string            `json:"containerId,omitempty"`
	LinuxServerID              string            `json:"linuxServerId,omitempty"`
	ContainerName              string            `json:"containerName,omitempty"`
	PortStart                  int               `json:"portStart,omitempty"`
	PortEnd                    int               `json:"portEnd,omitempty"`
	MaxProcesses               int               `json:"maxProcesses,omitempty"`
	SessionRoot                string            `json:"sessionRoot,omitempty"`
	ConfigDir                  string            `json:"configDir,omitempty"`
	CurrentProcesses           int               `json:"currentProcesses"`
	MetricsSource              string            `json:"metricsSource,omitempty"`
	CPUUsagePercent            *float64          `json:"cpuUsagePercent,omitempty"`
	MemoryMaxBytes             *int64            `json:"memoryMaxBytes,omitempty"`
	MemoryUsedBytes            *int64            `json:"memoryUsedBytes,omitempty"`
	MemoryUsagePercent         *float64          `json:"memoryUsagePercent,omitempty"`
	DiskReadBytesPerSecond     *float64          `json:"diskReadBytesPerSecond,omitempty"`
	DiskWriteBytesPerSecond    *float64          `json:"diskWriteBytesPerSecond,omitempty"`
	ManagedProcesses           []ManagedProcess  `json:"managedProcesses,omitempty"`
	Capabilities               map[string]any    `json:"capabilities,omitempty"`
	BackendProcessID           string            `json:"backendProcessId,omitempty"`
	CommandID                  string            `json:"commandId,omitempty"`
	Command                    string            `json:"command,omitempty"`
	Port                       int               `json:"port,omitempty"`
	TimeoutMillis              int64             `json:"timeoutMillis,omitempty"`
	Status                     string            `json:"status,omitempty"`
	PID                        int               `json:"pid,omitempty"`
	BaseURL                    string            `json:"baseUrl,omitempty"`
	SessionPath                string            `json:"sessionPath,omitempty"`
	ConfigPath                 string            `json:"configPath,omitempty"`
	Healthy                    *bool             `json:"healthy,omitempty"`
	Message                    string            `json:"message,omitempty"`
	ErrorCode                  string            `json:"errorCode,omitempty"`
	ConnectedBackendProcessIDs []string          `json:"connectedBackendProcessIds,omitempty"`
	BackendEndpoints           []BackendEndpoint `json:"backendEndpoints,omitempty"`
}

func traceID() string {
	now := time.Now().UTC()
	return "trace_" + now.Format("20060102150405") + "_" + now.Format("000000000")
}
