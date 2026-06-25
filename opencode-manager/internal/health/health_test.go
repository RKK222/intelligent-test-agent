package health

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/icbc/test-agent/opencode-manager/internal/state"
)

func TestCheckerReportsHealthyWhenPIDAliveAndGlobalHealthSucceeds(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/global/health" {
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

func TestCheckerFallsBackToDocEndpoint(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/global/health" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		if r.URL.Path == "/doc" {
			w.WriteHeader(http.StatusOK)
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

	if result.Status != StatusHealthy {
		t.Fatalf("expected /doc fallback to be healthy, got %#v", result)
	}
}

func TestCheckerReportsUnhealthyWhenPIDIsNotAlive(t *testing.T) {
	checker := Checker{ProcessAlive: func(pid int) bool { return false }}
	result := checker.Check(context.Background(), state.ProcessRecord{PID: 12345, BaseURL: "http://127.0.0.1:4096", Port: 4096})

	if result.Status != StatusUnhealthy {
		t.Fatalf("expected unhealthy result, got %#v", result)
	}
}
