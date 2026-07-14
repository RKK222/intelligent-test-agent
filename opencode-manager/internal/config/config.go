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

	defaultLinuxSysDataRootDir   = "/data/.testagent"
	defaultWindowsSysDataRootDir = "D:/data/.testagent"
	serverIDFileName             = ".serverid"
	serverHostFileName           = ".serverhost"
	managerProcessName           = "opencode-manager"

	defaultBackendPort          = 8080
	defaultBackendWebSocketPath = "/api/internal/platform/opencode-runtime/manager/ws"
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
	userHomeDir  func() (string, error)
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
		userHomeDir:  os.UserHomeDir,
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
	if r.userHomeDir == nil {
		r.userHomeDir = defaults.userHomeDir
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
	// ServerHost 是从 .serverhost 读取的可访问地址，只用于连接本机 Java 和生成用户进程 baseUrl。
	ServerHost   string
	PortStart    int
	PortEnd      int
	MaxProcesses int
	OpencodeBin  string
	StateDir     string
	SessionRoot  string
	ConfigDir    string
	AllowedCORS  []string
	// RuntimeConfigRequired 表示 run 模式必须先从 Java 公共参数拿到 session/config/max 后才能启动用户进程。
	RuntimeConfigRequired bool
}

// ControlConfig 扩展容器本地配置，描述 manager 与后端控制面通信所需参数。
type ControlConfig struct {
	Config
	ManagerID           string
	BackendWebSocketURL string
	Token               string
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
	linuxServerID, serverHost, err := resolveServerIdentityAndHost(rt)
	if err != nil {
		return Config{}, err
	}

	cfg := Config{
		ContainerID:   containerID,
		LinuxServerID: linuxServerID,
		ServerHost:    serverHost,
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
	linuxServerID, serverHost, err := resolveServerIdentityAndHost(rt)
	if err != nil {
		return ControlConfig{}, err
	}
	availablePorts := portEnd - portStart + 1
	base := Config{
		ContainerID:           containerID,
		LinuxServerID:         linuxServerID,
		ServerHost:            serverHost,
		PortStart:             portStart,
		PortEnd:               portEnd,
		MaxProcesses:          availablePorts,
		OpencodeBin:           envDefault("OPENCODE_BIN", defaultOpencodeBin),
		StateDir:              envDefault("OPENCODE_MANAGER_STATE_DIR", defaultStateDir),
		AllowedCORS:           splitCSV(os.Getenv("OPENCODE_ALLOWED_CORS")),
		RuntimeConfigRequired: true,
	}
	webSocketURL, err := derivedBackendWebSocketURL(serverHost)
	if err != nil {
		return ControlConfig{}, err
	}
	cfg := ControlConfig{
		Config:              base,
		ManagerID:           deriveManagerID(containerID),
		BackendWebSocketURL: webSocketURL,
		Token:               strings.TrimSpace(os.Getenv("OPENCODE_MANAGER_TOKEN")),
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
	if !isStableServerID(c.LinuxServerID) {
		return fmt.Errorf("linux server id must be 1-128 chars of letters, digits, dot, underscore or hyphen")
	}
	if !isValidServerHost(c.ServerHost) {
		return fmt.Errorf("server host must be a host name or IPv4 literal without scheme or port")
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
		return fmt.Errorf("derived manager id must start with mgr_")
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
	if c.HeartbeatInterval <= 0 || c.ReconnectInterval <= 0 {
		return fmt.Errorf("manager control intervals must be positive")
	}
	return nil
}

// String 返回脱敏后的控制配置摘要，禁止暴露 manager token。
func (c ControlConfig) String() string {
	return fmt.Sprintf(
		"managerId=%s containerId=%s linuxServerId=%s serverHost=%s webSocketUrl=%s token=<redacted> heartbeatInterval=%s reconnectInterval=%s",
		c.ManagerID,
		c.ContainerID,
		c.LinuxServerID,
		c.ServerHost,
		c.BackendWebSocketURL,
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

func resolveServerIdentityAndHost(rt configRuntime) (string, string, error) {
	if strings.EqualFold(rt.goos, "windows") {
		hostname, err := rt.hostname()
		if err != nil {
			return "", "", fmt.Errorf("Windows hostname lookup failed: %w", err)
		}
		linuxServerID := normalizeIdentifier(hostname)
		if !isStableServerID(linuxServerID) {
			return "", "", fmt.Errorf("Windows hostname must contain a stable linux server id")
		}
		// 优先读取 .serverhost 文件，允许用户通过环境变量或文件覆盖自动探测的 IP
		hostPath, err := serverHostFilePath(rt)
		if err == nil {
			if serverHost, err := waitForServerHostFile(rt, hostPath); err == nil {
				return linuxServerID, serverHost, nil
			}
		}
		ip, err := rt.localIPv4()
		if err != nil {
			return "", "", fmt.Errorf("detect Windows local IPv4 failed: %w", err)
		}
		ip = strings.TrimSpace(ip)
		if !isUsableIPv4(ip) {
			return "", "", fmt.Errorf("detected Windows local IPv4 is invalid")
		}
		return linuxServerID, ip, nil
	}

	idPath, err := serverIDFilePath(rt)
	if err != nil {
		return "", "", err
	}
	hostPath, err := serverHostFilePath(rt)
	if err != nil {
		return "", "", err
	}
	linuxServerID, err := waitForServerIDFile(rt, idPath)
	if err != nil {
		return "", "", err
	}
	serverHost, err := waitForServerHostFile(rt, hostPath)
	if err != nil {
		return "", "", err
	}
	return linuxServerID, serverHost, nil
}

func serverIDFilePath(rt configRuntime) (string, error) {
	root, err := sysDataRootDir(rt)
	if err != nil {
		return "", err
	}
	return filepath.Join(root, serverIDFileName), nil
}

func serverHostFilePath(rt configRuntime) (string, error) {
	root, err := sysDataRootDir(rt)
	if err != nil {
		return "", err
	}
	return filepath.Join(root, serverHostFileName), nil
}

func sysDataRootDir(rt configRuntime) (string, error) {
	if configured := strings.TrimSpace(os.Getenv("SYS_DATA_ROOT_DIR")); configured != "" {
		return configured, nil
	}
	switch strings.ToLower(rt.goos) {
	case "darwin":
		home, err := rt.userHomeDir()
		if err != nil {
			return "", fmt.Errorf("resolve SYS_DATA_ROOT_DIR default failed: %w", err)
		}
		home = strings.TrimSpace(home)
		if home == "" {
			return "", fmt.Errorf("resolve SYS_DATA_ROOT_DIR default failed: home directory is blank")
		}
		return filepath.Join(home, ".testagent"), nil
	case "windows":
		return defaultWindowsSysDataRootDir, nil
	default:
		return defaultLinuxSysDataRootDir, nil
	}
}

func waitForServerIDFile(rt configRuntime, path string) (string, error) {
	value, err := waitForRequiredFile(rt, path, "server identity file .serverid")
	if err != nil {
		return "", err
	}
	if !isStableServerID(value) {
		return "", fmt.Errorf("server identity file .serverid contains invalid stable id")
	}
	return value, nil
}

func waitForServerHostFile(rt configRuntime, path string) (string, error) {
	value, err := waitForRequiredFile(rt, path, "server host file .serverhost")
	if err != nil {
		return "", err
	}
	if !isValidServerHost(value) {
		return "", fmt.Errorf("server host file .serverhost contains invalid host")
	}
	return value, nil
}

func waitForRequiredFile(rt configRuntime, path string, label string) (string, error) {
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
			return strings.TrimSpace(string(raw)), nil
		}
		if !errors.Is(err, os.ErrNotExist) {
			return "", fmt.Errorf("read %s failed: %w", label, err)
		}
		if attempt < attempts {
			rt.sleep(rt.serverIPPoll)
		}
	}
	return "", fmt.Errorf("%s was not available within %s", label, rt.serverIPWait)
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

func derivedBackendWebSocketURL(serverHost string) (string, error) {
	rawPort := envDefault("OPENCODE_MANAGER_BACKEND_PORT", strconv.Itoa(defaultBackendPort))
	port, err := strconv.Atoi(rawPort)
	if err != nil || port < 1 || port > 65535 {
		return "", fmt.Errorf("OPENCODE_MANAGER_BACKEND_PORT must be an integer between 1 and 65535")
	}
	if !isValidServerHost(serverHost) {
		return "", fmt.Errorf("server host must be a host name or IPv4 literal without scheme or port")
	}
	return fmt.Sprintf("ws://%s:%d%s", serverHost, port, defaultBackendWebSocketPath), nil
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

func isStableServerID(value string) bool {
	value = strings.TrimSpace(value)
	if len(value) < 1 || len(value) > 128 {
		return false
	}
	for _, r := range value {
		valid := (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9')
		valid = valid || r == '.' || r == '_' || r == '-'
		if !valid {
			return false
		}
	}
	return true
}

func isValidServerHost(value string) bool {
	value = strings.TrimSpace(value)
	if len(value) < 1 || len(value) > 255 {
		return false
	}
	for i, r := range value {
		valid := (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9')
		if i > 0 {
			valid = valid || r == '.' || r == '_' || r == '-'
		}
		if !valid {
			return false
		}
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

// deriveManagerID 使用容器名和固定管理进程逻辑名生成内部 managerId，避免多容器共享默认环境变量时互相覆盖 Redis 快照。
func deriveManagerID(containerID string) string {
	return "mgr_" + normalizeIDSegment(containerID) + "_" + normalizeIDSegment(managerProcessName)
}

// normalizeIDSegment 保留 ASCII 字母数字并用下划线折叠其他字符，使派生 ID 稳定满足现有 mgr_ 前缀和标识符约束。
func normalizeIDSegment(value string) string {
	var builder strings.Builder
	lastUnderscore := false
	for _, r := range strings.TrimSpace(value) {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') {
			builder.WriteRune(r)
			lastUnderscore = false
			continue
		}
		if !lastUnderscore && builder.Len() > 0 {
			builder.WriteByte('_')
			lastUnderscore = true
		}
	}
	result := strings.Trim(builder.String(), "_")
	if result == "" {
		return "unknown"
	}
	return result
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
