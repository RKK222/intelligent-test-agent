package health

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/enterprise/test-agent/opencode-manager/internal/state"
)

func TestCheckerReportsHealthyWhenPIDAliveAndGlobalHealthSucceeds(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/global/health" && r.URL.Path != "/global/config" {
			t.Fatalf("unexpected path %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	checker := Checker{
		Client:       server.Client(),
		ProcessAlive: func(pid int) bool { return pid == 12345 },
		ProbeBaseURL: server.URL,
	}
	result := checker.Check(context.Background(), state.ProcessRecord{PID: 12345, BaseURL: "http://10.8.0.12:4096", Port: 4096})

	if result.Status != StatusHealthy {
		t.Fatalf("expected healthy result, got %#v", result)
	}
}

func TestCheckerReportsUnhealthyWhenGlobalHealthFails(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/global/health" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		t.Fatalf("unexpected path %s", r.URL.Path)
	}))
	defer server.Close()

	checker := Checker{
		Client:       server.Client(),
		ProcessAlive: func(pid int) bool { return true },
		ProbeBaseURL: server.URL,
	}
	result := checker.Check(context.Background(), state.ProcessRecord{PID: 12345, BaseURL: "http://10.8.0.12:4096", Port: 4096})

	if result.Status != StatusUnhealthy {
		t.Fatalf("expected unhealthy when /global/health fails, got %#v", result)
	}
	if result.Message != "opencode /global/health endpoint is not reachable" {
		t.Fatalf("expected specific message, got %s", result.Message)
	}
}

func TestCheckerReportsUnhealthyWhenGlobalConfigIsInvalid(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/global/health":
			w.WriteHeader(http.StatusOK)
		case "/global/config":
			w.WriteHeader(http.StatusBadRequest)
		default:
			t.Fatalf("unexpected path %s", r.URL.Path)
		}
	}))
	defer server.Close()

	checker := Checker{
		Client:       server.Client(),
		ProcessAlive: func(pid int) bool { return true },
		ProbeBaseURL: server.URL,
	}
	result := checker.Check(context.Background(), state.ProcessRecord{PID: 12345, BaseURL: "http://10.8.0.12:4096", Port: 4096})

	if result.Status != StatusUnhealthy {
		t.Fatalf("expected unhealthy when /global/config fails, got %#v", result)
	}
	if result.Message != "opencode configuration is not loadable" {
		t.Fatalf("expected config-specific message, got %s", result.Message)
	}
}

func TestCheckerReportsUnhealthyWhenPIDIsNotAlive(t *testing.T) {
	checker := Checker{ProcessAlive: func(pid int) bool { return false }}
	result := checker.Check(context.Background(), state.ProcessRecord{PID: 12345, BaseURL: "http://127.0.0.1:4096", Port: 4096})

	if result.Status != StatusUnhealthy {
		t.Fatalf("expected unhealthy result, got %#v", result)
	}
}
