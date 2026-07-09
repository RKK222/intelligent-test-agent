package main

import (
	"log"
	"os"
	"path/filepath"
	"strings"
	"testing"
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
