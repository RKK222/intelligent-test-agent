package config

import (
	"strings"
	"testing"
	"time"
)

func TestLoadControlFromEnvRequiresManagerSocketSettings(t *testing.T) {
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")
	t.Setenv("OPENCODE_MANAGER_LINUX_SERVER_ID", "10.8.0.12")
	t.Setenv("OPENCODE_MANAGER_PORT_START", "4096")
	t.Setenv("OPENCODE_MANAGER_PORT_END", "4100")
	t.Setenv("OPENCODE_MANAGER_MAX_PROCESSES", "5")

	_, err := LoadControlFromEnv()

	if err == nil {
		t.Fatalf("expected missing manager control settings error")
	}
}

func TestLoadControlFromEnvAppliesIntervalsAndHidesToken(t *testing.T) {
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")
	t.Setenv("OPENCODE_MANAGER_LINUX_SERVER_ID", "10.8.0.12")
	t.Setenv("OPENCODE_MANAGER_PORT_START", "4096")
	t.Setenv("OPENCODE_MANAGER_PORT_END", "4100")
	t.Setenv("OPENCODE_MANAGER_MAX_PROCESSES", "5")
	t.Setenv("OPENCODE_MANAGER_ID", "mgr_1234567890abcdef")
	t.Setenv("OPENCODE_MANAGER_BACKEND_DISCOVERY_URL", "http://10.8.0.21:8080/api/internal/platform/opencode-runtime/manager-backends")
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")
	t.Setenv("OPENCODE_MANAGER_DISCOVERY_INTERVAL", "3s")
	t.Setenv("OPENCODE_MANAGER_HEARTBEAT_INTERVAL", "4s")
	t.Setenv("OPENCODE_MANAGER_RECONNECT_INTERVAL", "5s")

	cfg, err := LoadControlFromEnv()
	if err != nil {
		t.Fatalf("LoadControlFromEnv returned error: %v", err)
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
