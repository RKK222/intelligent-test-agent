package config

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

const (
	defaultOpencodeBin = "opencode"
	defaultStateDir    = "/data/opencode/manager"
	defaultSessionRoot = "/data/opencode/session"
	defaultConfigDir   = "/data/opencode/.config/opencode/"
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
