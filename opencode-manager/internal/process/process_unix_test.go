//go:build !windows

package process

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/enterprise/test-agent/opencode-manager/internal/health"
)

func TestOSStarterReapsExitedChildProcess(t *testing.T) {
	pid, err := (OSStarter{}).Start(context.Background(), StartSpec{
		Command: "/bin/sh",
		Args:    []string{"-c", "exit 0"},
		LogPath: filepath.Join(t.TempDir(), "child.log"),
	})
	if err != nil {
		t.Fatalf("start child process: %v", err)
	}

	deadline := time.Now().Add(time.Second)
	for health.DefaultProcessAlive(pid) && time.Now().Before(deadline) {
		time.Sleep(10 * time.Millisecond)
	}
	if health.DefaultProcessAlive(pid) {
		t.Fatalf("exited child pid %d must be reaped instead of remaining alive as zombie", pid)
	}
}
