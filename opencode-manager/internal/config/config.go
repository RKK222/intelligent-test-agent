package config

import (
	"errors"
	"fmt"
	"net"
	"net/url"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"time"
)

const (
	defaultOpencodeBin = "opencode"
	defaultStateDir    = "/data/opencode/manager"
	defaultSessionRoot = "/data/opencode/session"
	defaultConfigDir   = "/data/opencode/.config/opencode/"

	defaultServerIPFile = "/data/.testagent/.serverip"

	defaultBackendPort          = 8080
	defaultBackendWebSocketPath = "/api/internal/platform/opencode-runtime/manager/ws"
	defaultDiscoveryInterval    = 10 * time.Second
	defaultHeartbeatInterval    = 5 * time.Second
	defaultReconnectInterval    = 10 * time.Second
	defaultServerIPWait         = 30 * time.Second
	defaultServerIPPoll         = time.Second
)

// configRuntime 封装少量操作系统能力，便于单元测试覆盖 Windows/非 Windows 分支。
type configRuntime struct {
	goos         string
	readFile     func(string) ([]byte, error)
	hostname     func() (string, error)
	localIPv4    func() (string, error)
	sleep        func(time.Duration)
	serverIPWait time.Duration
	serverIPPoll time.Duration
}

func defaultConfigRuntime() configRuntime {
	return configRuntime{
		goos:         runtime.GOOS,
		readFile:     os.ReadFile,
		hostname:     os.Hostname,
		localIPv4:    detectLocalIPv4,
		sleep:        time.Sleep,
		serverIPWait: defaultServerIPWait,
		serverIPPoll: defaultServerIPPoll,
	}
}

func (r configRuntime) withDefaults() configRuntime {
	defaults := defaultConfigRuntime()
	if r.goos == "" {
		r.goos = defaults.goos
	}
	if r.readFile == nil {
		r.readFile = defaults.readFile
	}
	if r.hostname == nil {
		r.hostname = defaults.hostname
	}
	if r.localIPv4 == nil {
		r.localIPv4 = defaults.localIPv4
	}
	if r.sleep == nil {
		r.sleep = defaults.sleep
	}
	if r.serverIPWait <= 0 {
		r.serverIPWait = defaults.serverIPWait
	}
	if r.serverIPPoll <= 0 {
		r.serverIPPoll = defaults.serverIPPoll
	}
	return r
}

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
	// RuntimeConfigRequired 表示 run 模式必须先从 Java 公共参数拿到 session/config/max 后才能启动用户进程。
	RuntimeConfigRequired bool
}

// ControlConfig 扩展容器本地配置，描述 manager 与后端控制面通信所需参数。
type ControlConfig struct {
	Config
	ManagerID           string
	BackendWebSocketURL string
	Token               string
	DiscoveryInterval   time.Duration
	HeartbeatInterval   time.Duration
	ReconnectInterval   time.Duration
}

// LoadFromEnv 从环境变量读取容器拓扑、端口池和路径配置。
func LoadFromEnv() (Config, error) {
	return loadFromEnvWithRuntime(defaultConfigRuntime())
}

func loadFromEnvWithRuntime(rt configRuntime) (Config, error) {
	rt = rt.withDefaults()
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
	containerID, err := resolveContainerID(rt)
	if err != nil {
		return Config{}, err
	}
	serverIP, err := resolveServerIP(rt)
	if err != nil {
		return Config{}, err
	}

	cfg := Config{
		ContainerID:   containerID,
		LinuxServerID: serverIP,
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
	return loadControlFromEnvWithRuntime(defaultConfigRuntime())
}

func loadControlFromEnvWithRuntime(rt configRuntime) (ControlConfig, error) {
	rt = rt.withDefaults()
	portStart, err := requiredInt("OPENCODE_MANAGER_PORT_START")
	if err != nil {
		return ControlConfig{}, err
	}
	portEnd, err := requiredInt("OPENCODE_MANAGER_PORT_END")
	if err != nil {
		return ControlConfig{}, err
	}
	containerID, err := resolveContainerID(rt)
	if err != nil {
		return ControlConfig{}, err
	}
	serverIP, err := resolveServerIP(rt)
	if err != nil {
		return ControlConfig{}, err
	}
	availablePorts := portEnd - portStart + 1
	base := Config{
		ContainerID:           containerID,
		LinuxServerID:         serverIP,
		PortStart:             portStart,
		PortEnd:               portEnd,
		MaxProcesses:          availablePorts,
		OpencodeBin:           envDefault("OPENCODE_BIN", defaultOpencodeBin),
		StateDir:              envDefault("OPENCODE_MANAGER_STATE_DIR", defaultStateDir),
		AllowedCORS:           splitCSV(os.Getenv("OPENCODE_ALLOWED_CORS")),
		RuntimeConfigRequired: true,
	}
	webSocketURL, err := derivedBackendWebSocketURL(base.LinuxServerID)
	if err != nil {
		return ControlConfig{}, err
	}
	cfg := ControlConfig{
		Config:              base,
		ManagerID:           strings.TrimSpace(os.Getenv("OPENCODE_MANAGER_ID")),
		BackendWebSocketURL: webSocketURL,
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
	if !isUsableIPv4(c.LinuxServerID) {
		return fmt.Errorf("linux server id must be a non-loopback IPv4")
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
	if !c.RuntimeConfigRequired && strings.TrimSpace(c.SessionRoot) == "" {
		return fmt.Errorf("OPENCODE_SESSION_ROOT must not be blank")
	}
	if !c.RuntimeConfigRequired && strings.TrimSpace(c.ConfigDir) == "" {
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
	if strings.TrimSpace(c.BackendWebSocketURL) == "" {
		return fmt.Errorf("backend WebSocket URL must not be blank")
	}
	parsed, err := url.Parse(c.BackendWebSocketURL)
	if err != nil || parsed.Host == "" || (parsed.Scheme != "ws" && parsed.Scheme != "wss") {
		return fmt.Errorf("backend WebSocket URL must be an absolute ws:// or wss:// URL")
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
		"managerId=%s containerId=%s linuxServerId=%s webSocketUrl=%s token=<redacted> discoveryInterval=%s heartbeatInterval=%s reconnectInterval=%s",
		c.ManagerID,
		c.ContainerID,
		c.LinuxServerID,
		c.BackendWebSocketURL,
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

func resolveServerIP(rt configRuntime) (string, error) {
	if strings.EqualFold(rt.goos, "windows") {
		ip, err := rt.localIPv4()
		if err != nil {
			return "", fmt.Errorf("detect Windows local IPv4 failed: %w", err)
		}
		ip = strings.TrimSpace(ip)
		if !isUsableIPv4(ip) {
			return "", fmt.Errorf("detected Windows local IPv4 is invalid")
		}
		return ip, nil
	}

	path := envDefault("OPENCODE_MANAGER_SERVER_IP_FILE", defaultServerIPFile)
	return waitForServerIPFile(rt, path)
}

func waitForServerIPFile(rt configRuntime, path string) (string, error) {
	attempts := int(rt.serverIPWait / rt.serverIPPoll)
	if rt.serverIPWait%rt.serverIPPoll != 0 {
		attempts++
	}
	if attempts < 1 {
		attempts = 1
	}

	for attempt := 0; attempt <= attempts; attempt++ {
		raw, err := rt.readFile(path)
		if err == nil {
			ip := strings.TrimSpace(string(raw))
			if !isUsableIPv4(ip) {
				return "", fmt.Errorf("server IP file .serverip contains invalid IPv4")
			}
			return ip, nil
		}
		if !errors.Is(err, os.ErrNotExist) {
			return "", fmt.Errorf("read server IP file .serverip failed: %w", err)
		}
		if attempt < attempts {
			rt.sleep(rt.serverIPPoll)
		}
	}
	return "", fmt.Errorf("server IP file .serverip was not available within %s", rt.serverIPWait)
}

func resolveContainerID(rt configRuntime) (string, error) {
	if strings.EqualFold(rt.goos, "windows") {
		hostname, err := rt.hostname()
		if err != nil {
			return "", fmt.Errorf("Windows hostname lookup failed: %w", err)
		}
		if id := normalizeIdentifier(hostname); id != "" {
			return id, nil
		}
		return "", fmt.Errorf("Windows hostname is blank")
	}
	if hostname, err := rt.hostname(); err == nil {
		if id := normalizeIdentifier(hostname); id != "" {
			return id, nil
		}
	}
	if raw, err := rt.readFile("/etc/hostname"); err == nil {
		if id := normalizeIdentifier(string(raw)); id != "" {
			return id, nil
		}
	}
	if configured := normalizeIdentifier(os.Getenv("OPENCODE_MANAGER_CONTAINER_ID")); configured != "" {
		return configured, nil
	}
	return "", fmt.Errorf("hostname, /etc/hostname or OPENCODE_MANAGER_CONTAINER_ID must contain a container id")
}

func derivedBackendWebSocketURL(serverIP string) (string, error) {
	rawPort := envDefault("OPENCODE_MANAGER_BACKEND_PORT", strconv.Itoa(defaultBackendPort))
	port, err := strconv.Atoi(rawPort)
	if err != nil || port < 1 || port > 65535 {
		return "", fmt.Errorf("OPENCODE_MANAGER_BACKEND_PORT must be an integer between 1 and 65535")
	}
	return fmt.Sprintf("ws://%s:%d%s", serverIP, port, defaultBackendWebSocketPath), nil
}

func detectLocalIPv4() (string, error) {
	interfaces, err := net.Interfaces()
	if err != nil {
		return "", err
	}
	candidates := make([]string, 0)
	for _, nif := range interfaces {
		if nif.Flags&net.FlagUp == 0 || nif.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, err := nif.Addrs()
		if err != nil {
			continue
		}
		for _, addr := range addrs {
			ip := ipv4FromAddr(addr)
			if isUsableIPv4(ip) {
				candidates = append(candidates, ip)
			}
		}
	}
	if len(candidates) == 0 {
		return "", fmt.Errorf("no non-loopback IPv4 address found")
	}
	sort.Strings(candidates)
	return candidates[0], nil
}

func ipv4FromAddr(addr net.Addr) string {
	switch value := addr.(type) {
	case *net.IPNet:
		if ip := value.IP.To4(); ip != nil {
			return ip.String()
		}
	case *net.IPAddr:
		if ip := value.IP.To4(); ip != nil {
			return ip.String()
		}
	}
	return ""
}

func isUsableIPv4(value string) bool {
	ip := net.ParseIP(strings.TrimSpace(value)).To4()
	if ip == nil {
		return false
	}
	if ip[0] == 0 || ip[0] == 127 {
		return false
	}
	if ip[0] == 169 && ip[1] == 254 {
		return false
	}
	if ip[0] >= 224 {
		return false
	}
	return true
}

func normalizeIdentifier(value string) string {
	fields := strings.Fields(value)
	if len(fields) == 0 {
		return ""
	}
	return strings.Join(fields, "-")
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
