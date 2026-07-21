package process

import (
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestBuildStartSpecUsesUnifiedAuthIDTimestampAndPortForLogPath(t *testing.T) {
	cfg := testConfig(t)
	startedAt := time.Date(2026, 7, 21, 8, 15, 30, 123456789, time.UTC)

	spec, err := buildStartSpec(cfg, StartRequest{
		Port:          4096,
		UnifiedAuthID: "DEV_888888888",
		SessionPath:   "/tmp/sessions/users/DEV_888888888",
		TraceID:       "trace_1234567890abcdef",
	}, startedAt)
	if err != nil {
		t.Fatalf("buildStartSpec returned error: %v", err)
	}

	want := filepath.Join(cfg.StateDir, "logs", "DEV_888888888-20260721T081530.123456789Z-4096.log")
	if spec.LogPath != want || !spec.StartedAt.Equal(startedAt) || spec.UnifiedAuthID != "DEV_888888888" {
		t.Fatalf("unexpected start spec: %#v", spec)
	}
}

func TestBuildStartSpecRejectsMismatchedUnifiedAuthID(t *testing.T) {
	_, err := buildStartSpec(testConfig(t), StartRequest{
		Port:          4096,
		UnifiedAuthID: "U001",
		SessionPath:   "/tmp/sessions/users/U002",
		TraceID:       "trace_1234567890abcdef",
	}, time.Date(2026, 7, 21, 8, 15, 30, 0, time.UTC))
	if err == nil || !strings.Contains(err.Error(), "does not match session path") {
		t.Fatalf("expected identity mismatch, got %v", err)
	}
	if strings.Contains(err.Error(), "U001") || strings.Contains(err.Error(), "U002") {
		t.Fatalf("identity mismatch must not expose raw identities: %v", err)
	}
}

func TestBuildStartSpecDerivesUnifiedAuthIDFromStableSessionPath(t *testing.T) {
	cfg := testConfig(t)
	startedAt := time.Date(2026, 7, 21, 8, 15, 30, 0, time.UTC)

	spec, err := buildStartSpec(cfg, StartRequest{
		Port:        4096,
		SessionPath: "/tmp/sessions/users/DEV_888888888",
		TraceID:     "trace_1234567890abcdef",
	}, startedAt)
	if err != nil {
		t.Fatalf("buildStartSpec returned error: %v", err)
	}

	want := filepath.Join(cfg.StateDir, "logs", "DEV_888888888-20260721T081530.000000000Z-4096.log")
	if spec.UnifiedAuthID != "DEV_888888888" || spec.LogPath != want {
		t.Fatalf("expected identity derived from session path, got %#v", spec)
	}
}

func TestBuildStartSpecKeepsLegacyPortLogForLocalSessionPath(t *testing.T) {
	cfg := testConfig(t)

	spec, err := buildStartSpec(cfg, StartRequest{
		Port:        4096,
		SessionPath: "/tmp/sessions/4096",
		TraceID:     "trace_1234567890abcdef",
	}, time.Date(2026, 7, 21, 8, 15, 30, 0, time.UTC))
	if err != nil {
		t.Fatalf("buildStartSpec returned error: %v", err)
	}
	if spec.UnifiedAuthID != "" || spec.LogPath != cfg.LogPath(4096) {
		t.Fatalf("expected legacy port log path, got %#v", spec)
	}
}

func TestSafeLogIdentityEncodesUnsafeBytesAndBoundsLongValues(t *testing.T) {
	encoded := safeLogIdentity("用户/..\\\r\n")
	if encoded != "%E7%94%A8%E6%88%B7%2F%2E%2E%5C%0D%0A" {
		t.Fatalf("unexpected encoded identity %q", encoded)
	}
	first := safeLogIdentity(strings.Repeat("A", 255))
	second := safeLogIdentity(strings.Repeat("A", 254) + "B")
	if first == second || !strings.Contains(first, "-sha256-") || len(first) > 160 {
		t.Fatalf("long identities were not safely bounded: %q / %q", first, second)
	}
}
