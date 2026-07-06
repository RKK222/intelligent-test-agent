package config

import (
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestLoadFromEnvReadsServerIdentityAndHostFilesFromSysDataRootOnLinux(t *testing.T) {
	setBaseManagerEnv(t)

	cfg, err := loadFromEnvWithRuntime(testRuntime("linux", map[string]string{
		"/data/.testagent/.serverid":   "linux-prod-a\n",
		"/data/.testagent/.serverhost": "10.8.0.12\n",
		"/etc/hostname":                "container-abc123\n",
	}))
	if err != nil {
		t.Fatalf("loadFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.LinuxServerID != "linux-prod-a" {
		t.Fatalf("expected server identity from .serverid file, got %q", cfg.LinuxServerID)
	}
	if cfg.ContainerID != "container-abc123" {
		t.Fatalf("expected container id from /etc/hostname, got %q", cfg.ContainerID)
	}
}

func TestLoadFromEnvUsesSysDataRootDirEnvOverride(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("SYS_DATA_ROOT_DIR", "/data/testagent/data")

	cfg, err := loadFromEnvWithRuntime(testRuntime("linux", map[string]string{
		"/data/testagent/data/.serverid":   "linux-prod-a\n",
		"/data/testagent/data/.serverhost": "10.8.0.12\n",
		"/etc/hostname":                    "container-abc123\n",
	}))
	if err != nil {
		t.Fatalf("loadFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.LinuxServerID != "linux-prod-a" {
		t.Fatalf("expected server identity from SYS_DATA_ROOT_DIR override, got %q", cfg.LinuxServerID)
	}
}

func TestLoadFromEnvReadsServerIdentityFileFromSysDataRootOnDarwin(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")
	rt := testRuntime("darwin", map[string]string{
		"/Users/kaka/.testagent/.serverid":   "mac-build-a\n",
		"/Users/kaka/.testagent/.serverhost": "10.8.0.13\n",
	})
	rt.userHomeDir = func() (string, error) {
		return "/Users/kaka", nil
	}

	cfg, err := loadFromEnvWithRuntime(rt)
	if err != nil {
		t.Fatalf("loadFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.LinuxServerID != "mac-build-a" {
		t.Fatalf("expected server identity from SYS_DATA_ROOT_DIR-derived .serverid, got %q", cfg.LinuxServerID)
	}
}

func TestLoadFromEnvPrefersHostnameOverEtcHostnameAndEnvOnNonWindows(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_env")
	rt := testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
		"/etc/hostname":                     "ctr_file\n",
	})
	rt.hostname = func() (string, error) {
		return "ctr_hostname", nil
	}

	cfg, err := loadFromEnvWithRuntime(rt)
	if err != nil {
		t.Fatalf("loadFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.ContainerID != "ctr_hostname" {
		t.Fatalf("expected container id from hostname first, got %q", cfg.ContainerID)
	}
}

func TestLoadFromEnvFallsBackToEtcHostnameBeforeEnvOnNonWindows(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_env")
	rt := testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
		"/etc/hostname":                     "ctr_file\n",
	})
	rt.hostname = func() (string, error) {
		return " ", nil
	}

	cfg, err := loadFromEnvWithRuntime(rt)
	if err != nil {
		t.Fatalf("loadFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.ContainerID != "ctr_file" {
		t.Fatalf("expected container id from /etc/hostname before env, got %q", cfg.ContainerID)
	}
}

func TestLoadFromEnvFallsBackToEnvWhenHostnameSourcesBlankOnNonWindows(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_env")
	rt := testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
		"/etc/hostname":                     " \n",
	})
	rt.hostname = func() (string, error) {
		return " ", nil
	}

	cfg, err := loadFromEnvWithRuntime(rt)
	if err != nil {
		t.Fatalf("loadFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.ContainerID != "ctr_env" {
		t.Fatalf("expected container id from env fallback, got %q", cfg.ContainerID)
	}
}

func TestLoadFromEnvDoesNotUseHostNameEnvFallbackOnNonWindows(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("HOSTNAME", "ctr_host_env")
	rt := testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
		"/etc/hostname":                     " \n",
	})
	rt.hostname = func() (string, error) {
		return " ", nil
	}

	_, err := loadFromEnvWithRuntime(rt)

	if err == nil || !strings.Contains(err.Error(), "OPENCODE_MANAGER_CONTAINER_ID") {
		t.Fatalf("expected missing container id error without OPENCODE_MANAGER_CONTAINER_ID fallback, got %v", err)
	}
}

func TestLoadFromEnvWaitsForDelayedServerIdentityAndHostFiles(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")

	readAttempts := 0
	rt := testRuntime("linux", nil)
	rt.serverIPWait = 30 * time.Second
	rt.serverIPPoll = time.Second
	rt.readFile = func(path string) ([]byte, error) {
		switch path {
		case defaultLinuxServerIDFileForTest():
			readAttempts++
			if readAttempts < 4 {
				return nil, os.ErrNotExist
			}
			return []byte("linux-prod-a\n"), nil
		case defaultLinuxServerHostFileForTest():
			return []byte("10.8.0.21\n"), nil
		default:
			return nil, os.ErrNotExist
		}
	}

	cfg, err := loadFromEnvWithRuntime(rt)
	if err != nil {
		t.Fatalf("loadFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.LinuxServerID != "linux-prod-a" {
		t.Fatalf("expected delayed server identity file to be used, got %q", cfg.LinuxServerID)
	}
	if readAttempts != 4 {
		t.Fatalf("expected four server identity file read attempts, got %d", readAttempts)
	}
}

func TestLoadFromEnvFailsWhenServerIdentityFileTimesOut(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")

	rt := testRuntime("linux", nil)
	rt.serverIPWait = 2 * time.Second
	rt.serverIPPoll = time.Second

	_, err := loadFromEnvWithRuntime(rt)

	if err == nil || !strings.Contains(err.Error(), ".serverid") {
		t.Fatalf("expected safe timeout error for missing .serverid file, got %v", err)
	}
}

func TestLoadFromEnvFailsWhenServerHostFileTimesOut(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")

	_, err := loadFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest(): "linux-prod-a\n",
	}))

	if err == nil || !strings.Contains(err.Error(), ".serverhost") {
		t.Fatalf("expected safe timeout error for missing .serverhost file, got %v", err)
	}
}

func TestLoadFromEnvFailsWhenServerIDFileContainsInvalidStableID(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")

	_, err := loadFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "server/a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
	}))

	if err == nil || !strings.Contains(err.Error(), "server identity file .serverid contains invalid stable id") {
		t.Fatalf("expected invalid stable id error, got %v", err)
	}
}

func TestLoadFromEnvWindowsUsesMachineNameAsLinuxServerId(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_env")
	rt := testRuntime("windows", nil)
	rt.hostname = func() (string, error) {
		return "WIN-DEV-01", nil
	}
	rt.localIPv4 = func() (string, error) {
		return "192.168.10.25", nil
	}
	rt.readFile = func(path string) ([]byte, error) {
		t.Fatalf("windows branch must not read server identity files %q", path)
		return nil, errors.New("unexpected read")
	}

	cfg, err := loadFromEnvWithRuntime(rt)
	if err != nil {
		t.Fatalf("loadFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.LinuxServerID != "WIN-DEV-01" {
		t.Fatalf("expected Windows linuxServerId to be hostname, got %q", cfg.LinuxServerID)
	}
	if cfg.ContainerID != "WIN-DEV-01" {
		t.Fatalf("expected Windows container id to be hostname, got %q", cfg.ContainerID)
	}
}

func TestLoadFromEnvFailsWhenContainerIDCannotBeResolved(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("HOSTNAME", " ")

	rt := testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
		"/etc/hostname":                     " \n",
	})

	_, err := loadFromEnvWithRuntime(rt)

	if err == nil || !strings.Contains(err.Error(), "OPENCODE_MANAGER_CONTAINER_ID") {
		t.Fatalf("expected missing container id error, got %v", err)
	}
}

func TestLoadFromEnvValidatesPortRangeAndCapacity(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")
	t.Setenv("OPENCODE_MANAGER_PORT_START", "4100")
	t.Setenv("OPENCODE_MANAGER_PORT_END", "4096")
	t.Setenv("OPENCODE_MANAGER_MAX_PROCESSES", "4")

	_, err := loadFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
	}))
	if err == nil {
		t.Fatalf("expected invalid port range error")
	}

	t.Setenv("OPENCODE_MANAGER_PORT_START", "4096")
	t.Setenv("OPENCODE_MANAGER_PORT_END", "4097")
	t.Setenv("OPENCODE_MANAGER_MAX_PROCESSES", "3")

	_, err = loadFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
	}))
	if err == nil {
		t.Fatalf("expected max processes to be limited by available ports")
	}
}

func TestLoadFromEnvAppliesDefaultsAndCors(t *testing.T) {
	setBaseManagerEnv(t)
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")
	t.Setenv("OPENCODE_ALLOWED_CORS", "http://localhost:3000,http://127.0.0.1:3000")

	cfg, err := loadFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
	}))
	if err != nil {
		t.Fatalf("loadFromEnvWithRuntime returned error: %v", err)
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

func TestLoadControlFromEnvDoesNotRequireRuntimeCommonParameterEnv(t *testing.T) {
	t.Setenv("HOSTNAME", "")
	t.Setenv("OPENCODE_MANAGER_CONTAINER_ID", "ctr_01")
	t.Setenv("OPENCODE_MANAGER_PORT_START", "4096")
	t.Setenv("OPENCODE_MANAGER_PORT_END", "4100")
	t.Setenv("OPENCODE_MANAGER_TOKEN", "manager-secret")

	cfg, err := loadControlFromEnvWithRuntime(testRuntime("linux", map[string]string{
		defaultLinuxServerIDFileForTest():   "linux-prod-a\n",
		defaultLinuxServerHostFileForTest(): "10.8.0.12\n",
	}))
	if err != nil {
		t.Fatalf("loadControlFromEnvWithRuntime returned error: %v", err)
	}

	if cfg.MaxProcesses != 5 {
		t.Fatalf("expected run mode to use port capacity as registration placeholder, got %d", cfg.MaxProcesses)
	}
	if cfg.SessionRoot != "" || cfg.ConfigDir != "" {
		t.Fatalf("run mode must wait for common parameters, got session=%q config=%q", cfg.SessionRoot, cfg.ConfigDir)
	}
	if !cfg.RuntimeConfigRequired {
		t.Fatalf("expected run mode to require runtime config update")
	}
}

func setBaseManagerEnv(t *testing.T) {
	t.Helper()
	t.Setenv("HOSTNAME", "")
	t.Setenv("OPENCODE_MANAGER_PORT_START", "4096")
	t.Setenv("OPENCODE_MANAGER_PORT_END", "4100")
	t.Setenv("OPENCODE_MANAGER_MAX_PROCESSES", "4")
}

func defaultLinuxServerIDFileForTest() string {
	return filepath.Join(defaultLinuxSysDataRootDir, serverIDFileName)
}

func defaultLinuxServerHostFileForTest() string {
	return filepath.Join(defaultLinuxSysDataRootDir, serverHostFileName)
}

func testRuntime(goos string, files map[string]string) configRuntime {
	return configRuntime{
		goos: goos,
		readFile: func(path string) ([]byte, error) {
			if files != nil {
				if value, ok := files[path]; ok {
					return []byte(value), nil
				}
			}
			return nil, os.ErrNotExist
		},
		hostname: func() (string, error) {
			return "", nil
		},
		userHomeDir: func() (string, error) {
			return "/Users/tester", nil
		},
		localIPv4: func() (string, error) {
			return "10.8.0.12", nil
		},
		sleep:        func(time.Duration) {},
		serverIPWait: defaultServerIPWait,
		serverIPPoll: defaultServerIPPoll,
	}
}
