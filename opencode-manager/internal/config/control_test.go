package config

import (
	"strings"
	"testing"
	"time"
)

func TestLoadControlFromEnvRequiresManagerSocketSettings(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")

	_, err := loadControlFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultServerIPFile: "10.8.0.12\n",
	}))

	if err == nil {
		t.Fatalf("expected missing manager control settings error")
	}
}

func TestLoadControlFromEnvDerivesWebSocketURLFromServerIPAndBackendPort(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")
	t.Setenv("OPENCODE_MANAGER_ID", "mgr_1234567890abcdef")
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")
	t.Setenv("OPENCODE_MANAGER_BACKEND_PORT", "18080")

	cfg, err := loadControlFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultServerIPFile: "10.8.0.12\n",
	}))
	if err != nil {
		t.Fatalf("loadControlFromEnvWithRuntime returned error: %v", err)
	}

	expected := "ws://10.8.0.12:18080/api/internal/platform/opencode-runtime/manager/ws"
	if cfg.BackendWebSocketURL != expected {
		t.Fatalf("expected derived WebSocket URL %q, got %q", expected, cfg.BackendWebSocketURL)
	}
}

func TestLoadControlFromEnvIgnoresLegacyDiscoveryURL(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")
	t.Setenv("OPENCODE_MANAGER_ID", "mgr_1234567890abcdef")
	t.Setenv("OPENCODE_MANAGER_BACKEND_DISCOVERY_URL", "http://backend.internal:8080/api/custom/discovery")
	t.Setenv("OPENCODE_MANAGER_BACKEND_PORT", "18080")
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")

	cfg, err := loadControlFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultServerIPFile: "10.8.0.12\n",
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
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")
	t.Setenv("OPENCODE_MANAGER_ID", "mgr_1234567890abcdef")
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")
	t.Setenv("OPENCODE_MANAGER_DISCOVERY_INTERVAL", "3s")
	t.Setenv("OPENCODE_MANAGER_HEARTBEAT_INTERVAL", "4s")
	t.Setenv("OPENCODE_MANAGER_RECONNECT_INTERVAL", "5s")

	cfg, err := loadControlFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultServerIPFile: "10.8.0.12\n",
	}))
	if err != nil {
		t.Fatalf("loadControlFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.ManagerID != "mgr_1234567890abcdef" {
		t.Fatalf("unexpected manager id %q", cfg.ManagerID)
	}
	if cfg.DiscoveryInterval != 3*time.Second || cfg.HeartbeatInterval != 4*time.Second || cfg.ReconnectInterval != 5*time.Second {
		t.Fatalf("unexpected intervals: %#v", cfg)
	}
	if strings.Contains(cfg.String(), "manager-secret") {
		t.Fatalf("control config string must not expose token")
	}
}

func TestLoadControlFromEnvUsesFiveSecondHeartbeatAndTenSecondReconnectDefaults(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")
	t.Setenv("OPENCODE_MANAGER_ID", "mgr_1234567890abcdef")
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")

	cfg, err := loadControlFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultServerIPFile: "10.8.0.12\n",
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
