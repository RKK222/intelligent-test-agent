package config

import (
	"strings"
	"testing"
	"time"
)

func TestLoadControlFromEnvRequiresManagerSocketSettings(t *testing.T) {
	setBaseManagerEnv(t)

	_, err := loadControlFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
	}))

	if err == nil {
		t.Fatalf("expected missing manager control settings error")
	}
}

func TestLoadControlFromEnvDerivesWebSocketURLFromServerHostAndBackendPort(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")
	t.Setenv("OPENCODE_MANAGER_BACKEND_PORT", "18080")

	cfg, err := loadControlFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "backend.internal\n",
	}))
	if err != nil {
		t.Fatalf("loadControlFromEnvWithRuntime returned error: %v", err)
	}

	expected := "ws://backend.internal:18080/api/internal/platform/opencode-runtime/manager/ws"
	if cfg.BackendWebSocketURL != expected {
		t.Fatalf("expected derived WebSocket URL %q, got %q", expected, cfg.BackendWebSocketURL)
	}
	if cfg.LinuxServerID != "linux-prod-a" {
		t.Fatalf("expected stable linux server id from .serverid, got %q", cfg.LinuxServerID)
	}
	if cfg.ServerHost != "backend.internal" {
		t.Fatalf("expected server host from .serverhost, got %q", cfg.ServerHost)
	}
}

func TestLoadControlFromEnvIgnoresLegacyDiscoveryURL(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_BACKEND_DISCOVERY_URL", "http://backend.internal:8080/api/custom/discovery")
	t.Setenv("OPENCODE_MANAGER_BACKEND_PORT", "18080")
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")

	cfg, err := loadControlFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
	}))
	if err != nil {
		t.Fatalf("loadControlFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.BackendWebSocketURL != "ws://10.8.0.12:18080/api/internal/platform/opencode-runtime/manager/ws" {
		t.Fatalf("expected legacy discovery URL to be ignored, got %q", cfg.BackendWebSocketURL)
	}
}

func TestLoadControlFromEnvAppliesIntervalsAndHidesToken(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")
	t.Setenv("OPENCODE_MANAGER_HEARTBEAT_INTERVAL", "4s")
	t.Setenv("OPENCODE_MANAGER_RECONNECT_INTERVAL", "5s")

	cfg, err := loadControlFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
	}))
	if err != nil {
		t.Fatalf("loadControlFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.ManagerID != deriveManagerID(deriveContainerID("linux-prod-a")) {
		t.Fatalf("unexpected manager id %q", cfg.ManagerID)
	}
	if cfg.HeartbeatInterval != 4*time.Second || cfg.ReconnectInterval != 5*time.Second {
		t.Fatalf("unexpected intervals: %#v", cfg)
	}
	if strings.Contains(cfg.String(), "manager-secret") {
		t.Fatalf("control config string must not expose token")
	}
}

func TestLoadControlFromEnvUsesFiveSecondHeartbeatAndTenSecondReconnectDefaults(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")

	cfg, err := loadControlFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
	}))
	if err != nil {
		t.Fatalf("loadControlFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.HeartbeatInterval != 5*time.Second {
		t.Fatalf("expected default heartbeat interval 5s, got %s", cfg.HeartbeatInterval)
	}
	if cfg.ReconnectInterval != 10*time.Second {
		t.Fatalf("expected default reconnect interval 10s, got %s", cfg.ReconnectInterval)
	}
}

func TestLoadControlFromEnvKeepsIdentityStableWhenContainerNameChanges(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_ID", "mgr_should_be_ignored")
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")
	files := map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
	}
	firstRuntime := testRuntime("linux", files)
	firstRuntime.hostname = func() (string, error) { return "worker-before", nil }
	secondRuntime := testRuntime("linux", files)
	secondRuntime.hostname = func() (string, error) { return "worker-after", nil }

	first, err := loadControlFromEnvWithRuntime(firstRuntime)
	if err != nil {
		t.Fatalf("loadControlFromEnvWithRuntime returned error: %v", err)
	}
	second, err := loadControlFromEnvWithRuntime(secondRuntime)
	if err != nil {
		t.Fatalf("second loadControlFromEnvWithRuntime returned error: %v", err)
	}

	if first.ContainerID != second.ContainerID || first.ManagerID != second.ManagerID {
		t.Fatalf("container rename changed stable identity: first=%#v second=%#v", first, second)
	}
	if first.ContainerName != "worker-before" || second.ContainerName != "worker-after" {
		t.Fatalf("expected readable container names to be preserved: first=%q second=%q", first.ContainerName, second.ContainerName)
	}
	if first.ManagerID != deriveManagerID(deriveContainerID("linux-prod-a")) {
		t.Fatalf("unexpected manager id %q", first.ManagerID)
	}
}

func TestLoadControlFromEnvSeparatesServersWithTheSameContainerName(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")
	firstRuntime := testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
	})
	secondRuntime := testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-b\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.13\n",
	})

	first, err := loadControlFromEnvWithRuntime(firstRuntime)
	if err != nil {
		t.Fatalf("first loadControlFromEnvWithRuntime returned error: %v", err)
	}
	second, err := loadControlFromEnvWithRuntime(secondRuntime)
	if err != nil {
		t.Fatalf("second loadControlFromEnvWithRuntime returned error: %v", err)
	}

	if first.ContainerName != second.ContainerName {
		t.Fatalf("test setup must use the same readable container name: %q != %q", first.ContainerName, second.ContainerName)
	}
	if first.ContainerID == second.ContainerID || first.ManagerID == second.ManagerID {
		t.Fatalf("different linux server ids must produce different runtime identities: first=%#v second=%#v", first, second)
	}
}

func TestLoadControlFromEnvDerivesManagerIDFromEtcHostnameWhenHostnameBlank(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")
	rt := testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
		"/etc/hostname":                     "ctr-file-01\n",
	})
	rt.hostname = func() (string, error) {
		return " ", nil
	}

	cfg, err := loadControlFromEnvWithRuntime(rt)
	if err != nil {
		t.Fatalf("loadControlFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.ManagerID != deriveManagerID(deriveContainerID("linux-prod-a")) {
		t.Fatalf("unexpected manager id %q", cfg.ManagerID)
	}
	if cfg.ContainerName != "ctr-file-01" {
		t.Fatalf("unexpected container name %q", cfg.ContainerName)
	}
}

func TestLoadControlFromEnvDoesNotUseContainerIDEnvFallback(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr-env-01")
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")
	rt := testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
		"/etc/hostname":                     " \n",
	})
	rt.hostname = func() (string, error) {
		return " ", nil
	}

	_, err := loadControlFromEnvWithRuntime(rt)
	if err == nil || !strings.Contains(err.Error(), "hostname or /etc/hostname") {
		t.Fatalf("expected missing container name error without environment fallback, got %v", err)
	}
}
