package config

import "testing"

func TestLoadFromEnvRequiresContainerAndLinuxServer(t *testing.T) {
	t.Setenv("OPENCODE_MANAGER_PORT_START", "4096")
	t.Setenv("OPENCODE_MANAGER_PORT_END", "4100")
	t.Setenv("OPENCODE_MANAGER_MAX_PROCESSES", "4")

	_, err := LoadFromEnv()

	if err == nil {
		t.Fatalf("expected missing required env error")
	}
}

func TestLoadFromEnvValidatesPortRangeAndCapacity(t *testing.T) {
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")
	t.Setenv("OPENCODE_MANAGER_LINUX_SERVER_ID", "10.8.0.12")
	t.Setenv("OPENCODE_MANAGER_PORT_START", "4100")
	t.Setenv("OPENCODE_MANAGER_PORT_END", "4096")
	t.Setenv("OPENCODE_MANAGER_MAX_PROCESSES", "4")

	_, err := LoadFromEnv()
	if err == nil {
		t.Fatalf("expected invalid port range error")
	}

	t.Setenv("OPENCODE_MANAGER_PORT_START", "4096")
	t.Setenv("OPENCODE_MANAGER_PORT_END", "4097")
	t.Setenv("OPENCODE_MANAGER_MAX_PROCESSES", "3")

	_, err = LoadFromEnv()
	if err == nil {
		t.Fatalf("expected max processes to be limited by available ports")
	}
}

func TestLoadFromEnvAppliesDefaultsAndCors(t *testing.T) {
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")
	t.Setenv("OPENCODE_MANAGER_LINUX_SERVER_ID", "10.8.0.12")
	t.Setenv("OPENCODE_MANAGER_PORT_START", "4096")
	t.Setenv("OPENCODE_MANAGER_PORT_END", "4100")
	t.Setenv("OPENCODE_MANAGER_MAX_PROCESSES", "4")
	t.Setenv("OPENCODE_ALLOWED_CORS", "http://localhost:3000,http://127.0.0.1:3000")

	cfg, err := LoadFromEnv()
	if err != nil {
		t.Fatalf("LoadFromEnv returned error: %v", err)
	}

	if cfg.OpencodeBin != "opencode" {
		t.Fatalf("expected default opencode binary, got %q", cfg.OpencodeBin)
	}
	if cfg.StateDir != "/data/opencode/manager" {
		t.Fatalf("unexpected state dir %q", cfg.StateDir)
	}
	if cfg.SessionRoot != "/data/opencode/session" {
		t.Fatalf("unexpected session root %q", cfg.SessionRoot)
	}
	if cfg.ConfigDir != "/data/opencode/.config/opencode/" {
		t.Fatalf("unexpected config dir %q", cfg.ConfigDir)
	}
	if len(cfg.AllowedCORS) != 2 {
		t.Fatalf("expected two CORS origins, got %#v", cfg.AllowedCORS)
	}
}
