package main

import (
	"bytes"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"syscall"
	"testing"
	"time"

	"github.com/enterprise/test-agent/opencode-manager/internal/state"
)

func TestIsErrorLogLineDetectsFailureEvents(t *testing.T) {
	if !isErrorLogLine("event=manager_command_exit status=FAILED error=boom") {
		t.Fatalf("expected failed command line to be routed to manager-error.log")
	}
	if !isErrorLogLine("event=manager_command_exit status=FAILED") {
		t.Fatalf("expected failed status line to be routed to manager-error.log")
	}
	if isErrorLogLine("event=manager_command_exit status=STARTED") {
		t.Fatalf("did not expect normal command line to be routed to manager-error.log")
	}
}

func TestConfigureSupervisorLogsWritesManagerAndErrorFiles(t *testing.T) {
	stateDir := t.TempDir()
	defer log.SetOutput(os.Stderr)
	closeLogs, err := configureSupervisorLogs(stateDir)
	if err != nil {
		t.Fatalf("configureSupervisorLogs returned error: %v", err)
	}
	log.Print("event=manager_command_exit status=STARTED")
	log.Print("event=manager_command_exit status=error error=boom")
	closeLogs()

	managerLog, err := os.ReadFile(filepath.Join(stateDir, "logs", "manager.log"))
	if err != nil {
		t.Fatalf("read manager log: %v", err)
	}
	errorLog, err := os.ReadFile(filepath.Join(stateDir, "logs", "manager-error.log"))
	if err != nil {
		t.Fatalf("read manager error log: %v", err)
	}

	if !strings.Contains(string(managerLog), "status=STARTED") || !strings.Contains(string(managerLog), "status=error") {
		t.Fatalf("expected manager.log to contain both normal and error lines, got %q", string(managerLog))
	}
	if strings.Contains(string(errorLog), "status=STARTED") || !strings.Contains(string(errorLog), "status=error") {
		t.Fatalf("expected manager-error.log to contain only error line, got %q", string(errorLog))
	}
}

func TestSupervisorClearsPreviousContainerStateBeforeConnecting(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("helper process termination uses SIGTERM")
	}
	stateDir := t.TempDir()
	dataRoot := t.TempDir()
	if err := os.WriteFile(filepath.Join(dataRoot, ".serverid"), []byte("test-linux-server\n"), 0o600); err != nil {
		t.Fatalf("write server id: %v", err)
	}
	if err := os.WriteFile(filepath.Join(dataRoot, ".serverhost"), []byte("127.0.0.1\n"), 0o600); err != nil {
		t.Fatalf("write server host: %v", err)
	}
	store := state.NewFileStore(stateDir)
	if err := store.Save(state.ProcessRecord{
		Port:      14096,
		PID:       12,
		StartedAt: time.Now().UTC(),
		TraceID:   "trace_previous_container_generation",
	}); err != nil {
		t.Fatalf("save previous generation state: %v", err)
	}

	var output bytes.Buffer
	command := exec.Command(os.Args[0], "-test.run=TestSupervisorProcessHelper")
	command.Env = append(os.Environ(),
		"TEST_OPENCODE_MANAGER_SUPERVISOR_HELPER=1",
		"HOSTNAME=test-opencode-worker",
		"SYS_DATA_ROOT_DIR="+dataRoot,
		"OPENCODE_MANAGER_STATE_DIR="+stateDir,
		"OPENCODE_MANAGER_PORT_START=14096",
		"OPENCODE_MANAGER_PORT_END=15095",
		"OPENCODE_MANAGER_BACKEND_PORT=1",
		"OPENCODE_MANAGER_TOKEN=test-manager-token",
		"OPENCODE_MANAGER_HEARTBEAT_INTERVAL=50ms",
		"OPENCODE_MANAGER_RECONNECT_INTERVAL=50ms",
	)
	command.Stdout = &output
	command.Stderr = &output
	if err := command.Start(); err != nil {
		t.Fatalf("start supervisor helper: %v", err)
	}
	t.Cleanup(func() {
		if command.ProcessState == nil {
			_ = command.Process.Kill()
			_ = command.Wait()
		}
	})

	deadline := time.Now().Add(5 * time.Second)
	for {
		_, ok, err := store.Get(14096)
		if err != nil {
			t.Fatalf("read supervisor state: %v", err)
		}
		if !ok {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("previous generation state was not removed, output=%s", output.String())
		}
		time.Sleep(20 * time.Millisecond)
	}
	if err := command.Process.Signal(syscall.SIGTERM); err != nil {
		t.Fatalf("stop supervisor helper: %v", err)
	}
	if err := command.Wait(); err != nil {
		t.Fatalf("supervisor helper did not stop cleanly: %v output=%s", err, output.String())
	}
	managerLog, err := os.ReadFile(filepath.Join(stateDir, "logs", "manager.log"))
	if err != nil {
		t.Fatalf("read manager log: %v", err)
	}
	if !strings.Contains(string(managerLog), "event=manager_previous_generation_state_clear status=success removedCount=1") {
		t.Fatalf("expected startup state reset audit log, log=%s", managerLog)
	}
}

func TestSupervisorProcessHelper(t *testing.T) {
	if os.Getenv("TEST_OPENCODE_MANAGER_SUPERVISOR_HELPER") != "1" {
		return
	}
	os.Exit(runSupervisor())
}
