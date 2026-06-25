package config

import (
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

const (
	defaultOpencodeBin = "opencode"
	defaultStateDir    = "/data/opencode/manager"
	defaultSessionRoot = "/data/opencode/session"
	defaultConfigDir   = "/data/opencode/.config/opencode/"

	defaultDiscoveryInterval = 10 * time.Second
	defaultHeartbeatInterval = 10 * time.Second
	defaultReconnectInterval = 5 * time.Second
)

// Config 描述单个容器内 opencode-manager 的静态运行边界。
type Config struct {
	ContainerID   string
	LinuxServerID string
	PortStart     int
	PortEnd       int
	MaxProcesses  int
	OpencodeBin   string
	StateDir      string
	SessionRoot   string
	ConfigDir     string
	AllowedCORS   []string
}

// ControlConfig 扩展容器本地配置，描述 manager 与后端控制面通信所需参数。
type ControlConfig struct {
	Config
	ManagerID           string
	BackendDiscoveryURL string
	Token               string
	DiscoveryInterval   time.Duration
	HeartbeatInterval   time.Duration
	ReconnectInterval   time.Duration
}

// LoadFromEnv 从环境变量读取容器拓扑、端口池和路径配置。
func LoadFromEnv() (Config, error) {
	portStart, err := requiredInt("OPENCODE_MANAGER_PORT_START")
	if err != nil {
		return Config{}, err
	}
	portEnd, err := requiredInt("OPENCODE_MANAGER_PORT_END")
	if err != nil {
		return Config{}, err
	}
	maxProcesses, err := requiredInt("OPENCODE_MANAGER_MAX_PROCESSES")
	if err != nil {
		return Config{}, err
	}

	cfg := Config{
		ContainerID:   strings.TrimSpace(os.Getenv("OPENCODE_MANAGER_CONTAINER_ID")),
		LinuxServerID: strings.TrimSpace(os.Getenv("OPENCODE_MANAGER_LINUX_SERVER_ID")),
		PortStart:     portStart,
		PortEnd:       portEnd,
		MaxProcesses:  maxProcesses,
		OpencodeBin:   envDefault("OPENCODE_BIN", defaultOpencodeBin),
		StateDir:      envDefault("OPENCODE_MANAGER_STATE_DIR", defaultStateDir),
		SessionRoot:   envDefault("OPENCODE_SESSION_ROOT", defaultSessionRoot),
		ConfigDir:     envDefault("OPENCODE_CONFIG_DIR", defaultConfigDir),
		AllowedCORS:   splitCSV(os.Getenv("OPENCODE_ALLOWED_CORS")),
	}
	if err := cfg.Validate(); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

// LoadControlFromEnv 读取长运行 WebSocket 控制面所需配置。
func LoadControlFromEnv() (ControlConfig, error) {
	base, err := LoadFromEnv()
	if err != nil {
		return ControlConfig{}, err
	}
	cfg := ControlConfig{
		Config:              base,
		ManagerID:           strings.TrimSpace(os.Getenv("OPENCODE_MANAGER_ID")),
		BackendDiscoveryURL: strings.TrimSpace(os.Getenv("OPENCODE_MANAGER_BACKEND_DISCOVERY_URL")),
		Token:               strings.TrimSpace(os.Getenv("OPENCODE_MANAGER_TOKEN")),
		DiscoveryInterval:   durationDefault("OPENCODE_MANAGER_DISCOVERY_INTERVAL", defaultDiscoveryInterval),
		HeartbeatInterval:   durationDefault("OPENCODE_MANAGER_HEARTBEAT_INTERVAL", defaultHeartbeatInterval),
		ReconnectInterval:   durationDefault("OPENCODE_MANAGER_RECONNECT_INTERVAL", defaultReconnectInterval),
	}
	if err := cfg.ValidateControl(); err != nil {
		return ControlConfig{}, err
	}
	return cfg, nil
}

// Validate 校验必填拓扑字段和端口池容量，避免管理进程启动在错误容器配置上。
func (c Config) Validate() error {
	if strings.TrimSpace(c.ContainerID) == "" {
		return fmt.Errorf("OPENCODE_MANAGER_CONTAINER_ID is required")
	}
	if strings.TrimSpace(c.LinuxServerID) == "" {
		return fmt.Errorf("OPENCODE_MANAGER_LINUX_SERVER_ID is required")
	}
	if c.PortStart < 1 || c.PortEnd > 65535 || c.PortStart > c.PortEnd {
		return fmt.Errorf("port range must be between 1 and 65535")
	}
	availablePorts := c.PortEnd - c.PortStart + 1
	if c.MaxProcesses < 1 || c.MaxProcesses > availablePorts {
		return fmt.Errorf("OPENCODE_MANAGER_MAX_PROCESSES must be between 1 and available port count")
	}
	if strings.TrimSpace(c.OpencodeBin) == "" {
		return fmt.Errorf("OPENCODE_BIN must not be blank")
	}
	if strings.TrimSpace(c.StateDir) == "" {
		return fmt.Errorf("OPENCODE_MANAGER_STATE_DIR must not be blank")
	}
	if strings.TrimSpace(c.SessionRoot) == "" {
		return fmt.Errorf("OPENCODE_SESSION_ROOT must not be blank")
	}
	if strings.TrimSpace(c.ConfigDir) == "" {
		return fmt.Errorf("OPENCODE_CONFIG_DIR must not be blank")
	}
	return nil
}

// ValidatePort 确认目标端口属于当前容器可管理的端口池。
func (c Config) ValidatePort(port int) error {
	if port < c.PortStart || port > c.PortEnd {
		return fmt.Errorf("port %d is outside configured range %d-%d", port, c.PortStart, c.PortEnd)
	}
	return nil
}

// ValidateControl 校验 manager 控制面必填项，token 只做存在性检查，避免误写日志。
func (c ControlConfig) ValidateControl() error {
	if err := c.Config.Validate(); err != nil {
		return err
	}
	if !strings.HasPrefix(strings.TrimSpace(c.ManagerID), "mgr_") {
		return fmt.Errorf("OPENCODE_MANAGER_ID must start with mgr_")
	}
	if strings.TrimSpace(c.BackendDiscoveryURL) == "" {
		return fmt.Errorf("OPENCODE_MANAGER_BACKEND_DISCOVERY_URL is required")
	}
	parsed, err := url.Parse(c.BackendDiscoveryURL)
	if err != nil || parsed.Scheme == "" || parsed.Host == "" {
		return fmt.Errorf("OPENCODE_MANAGER_BACKEND_DISCOVERY_URL must be an absolute URL")
	}
	if strings.TrimSpace(c.Token) == "" {
		return fmt.Errorf("OPENCODE_MANAGER_TOKEN is required")
	}
	if c.DiscoveryInterval <= 0 || c.HeartbeatInterval <= 0 || c.ReconnectInterval <= 0 {
		return fmt.Errorf("manager control intervals must be positive")
	}
	return nil
}

// String 返回脱敏后的控制配置摘要，禁止暴露 manager token。
func (c ControlConfig) String() string {
	return fmt.Sprintf(
		"managerId=%s containerId=%s linuxServerId=%s discoveryUrl=%s token=<redacted> discoveryInterval=%s heartbeatInterval=%s reconnectInterval=%s",
		c.ManagerID,
		c.ContainerID,
		c.LinuxServerID,
		c.BackendDiscoveryURL,
		c.DiscoveryInterval,
		c.HeartbeatInterval,
		c.ReconnectInterval,
	)
}

// SessionPath 返回指定端口对应的 opencode XDG_DATA_HOME。
func (c Config) SessionPath(port int) string {
	return filepath.Join(c.SessionRoot, strconv.Itoa(port))
}

// LogPath 返回指定端口 opencode server 的本地日志路径。
func (c Config) LogPath(port int) string {
	return filepath.Join(c.StateDir, "logs", fmt.Sprintf("%d.log", port))
}

func requiredInt(name string) (int, error) {
	raw := strings.TrimSpace(os.Getenv(name))
	if raw == "" {
		return 0, fmt.Errorf("%s is required", name)
	}
	value, err := strconv.Atoi(raw)
	if err != nil {
		return 0, fmt.Errorf("%s must be an integer: %w", name, err)
	}
	return value, nil
}

func envDefault(name string, fallback string) string {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		return fallback
	}
	return value
}

func durationDefault(name string, fallback time.Duration) time.Duration {
	raw := strings.TrimSpace(os.Getenv(name))
	if raw == "" {
		return fallback
	}
	value, err := time.ParseDuration(raw)
	if err != nil {
		return fallback
	}
	return value
}

func splitCSV(raw string) []string {
	parts := strings.Split(raw, ",")
	values := make([]string, 0, len(parts))
	for _, part := range parts {
		value := strings.TrimSpace(part)
		if value != "" {
			values = append(values, value)
		}
	}
	return values
}
