package main

import (
	"archive/tar"
	"compress/gzip"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestJavaPropertiesWindowsPathRoundTrip(t *testing.T) {
	directory := t.TempDir()
	path := filepath.Join(directory, "client.properties")
	original := `C:\Users\tester\AppData\Local\TestAgent\local-opencode-client`
	if err := os.WriteFile(path, []byte("installRoot="+escapeProperty(original)+"\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	properties, err := readProperties(path)
	if err != nil {
		t.Fatal(err)
	}
	if properties["installRoot"] != original {
		t.Fatalf("Windows path changed: got %q want %q", properties["installRoot"], original)
	}
}

func TestExtractTarGzRejectsTraversal(t *testing.T) {
	directory := t.TempDir()
	archive := filepath.Join(directory, "runtime.tar.gz")
	file, err := os.Create(archive)
	if err != nil {
		t.Fatal(err)
	}
	gzipWriter := gzip.NewWriter(file)
	tarWriter := tar.NewWriter(gzipWriter)
	content := []byte("tampered")
	if err := tarWriter.WriteHeader(&tar.Header{Name: "../escape.exe", Mode: 0o600, Size: int64(len(content))}); err != nil {
		t.Fatal(err)
	}
	if _, err := tarWriter.Write(content); err != nil {
		t.Fatal(err)
	}
	if err := tarWriter.Close(); err != nil {
		t.Fatal(err)
	}
	if err := gzipWriter.Close(); err != nil {
		t.Fatal(err)
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}
	if err := extractTarGz(archive, filepath.Join(directory, "extract"), "jdk"); err == nil {
		t.Fatal("expected traversal archive to be rejected")
	}
}

func TestReleaseCoordinates(t *testing.T) {
	if !validVersion("20260827203045") || validVersion("2026-08-27") {
		t.Fatal("release version validation mismatch")
	}
	if validDigest("a6") || !validDigest("a6"+strings.Repeat("0", 62)) {
		t.Fatal("digest validation mismatch")
	}
}

func TestEnsureEnrolledKeepsExistingCredentials(t *testing.T) {
	directory := t.TempDir()
	paths := layout{
		configDir: filepath.Join(directory, "config"),
		stateDir:  filepath.Join(directory, "state"),
	}
	if err := os.MkdirAll(paths.configDir, 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(paths.stateDir, 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(paths.configDir, "credentials.properties"), []byte("credential\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := ensureEnrolled(paths, "invalid-version"); err != nil {
		t.Fatalf("existing credentials should skip interactive enrollment: %v", err)
	}
}

func TestRegularFileStateRejectsSymlink(t *testing.T) {
	directory := t.TempDir()
	target := filepath.Join(directory, "target")
	link := filepath.Join(directory, "credentials.properties")
	if err := os.WriteFile(target, []byte("credential\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(target, link); err != nil {
		t.Skipf("symlink unavailable: %v", err)
	}
	if _, err := regularFileState(link); err == nil {
		t.Fatal("credential symlink should be rejected")
	}
}
