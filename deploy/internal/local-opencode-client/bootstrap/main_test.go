package main

import (
	"crypto/sha256"
	"encoding/hex"
	"os"
	"path/filepath"
	"testing"
)

func TestVerifyResourceRejectsSymlinkAndDigestMismatch(t *testing.T) {
	directory := t.TempDir()
	resource := filepath.Join(directory, "launcher")
	if err := os.WriteFile(resource, []byte("safe launcher"), 0o755); err != nil {
		t.Fatal(err)
	}
	digest := sha256.Sum256([]byte("safe launcher"))
	if err := verifyResource(resource, hex.EncodeToString(digest[:]), true); err != nil {
		t.Fatalf("expected valid resource: %v", err)
	}
	if err := verifyResource(resource, string(make([]byte, 64)), true); err == nil {
		t.Fatal("expected invalid digest to be rejected")
	}
	symlink := filepath.Join(directory, "launcher-link")
	if err := os.Symlink(resource, symlink); err != nil {
		t.Fatal(err)
	}
	if err := verifyResource(symlink, hex.EncodeToString(digest[:]), true); err == nil {
		t.Fatal("expected symlink resource to be rejected")
	}
}

func TestTerminalCommandsDoNotUseShellInterpolation(t *testing.T) {
	executable := "/tmp/TestAgent Local Client"
	commands := terminalCommands(executable)
	if len(commands) < 2 {
		t.Fatal("expected Kylin and fallback terminal candidates")
	}
	for _, command := range commands {
		if command.name == "sh" || command.name == "bash" {
			t.Fatalf("terminal command must not invoke a shell: %s", command.name)
		}
		foundExecutable := false
		for _, argument := range command.args {
			if argument == executable {
				foundExecutable = true
			}
		}
		if !foundExecutable {
			t.Fatalf("terminal %s does not receive the executable as one argument", command.name)
		}
	}
}
