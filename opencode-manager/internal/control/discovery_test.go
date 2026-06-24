package control

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestDiscoveryClientSendsBearerTokenAndParsesBackends(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer manager-secret" {
			t.Fatalf("missing authorization header: %q", r.Header.Get("Authorization"))
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{
			"success": true,
			"data": [{
				"backendProcessId": "bjp_1234567890abcdef",
				"linuxServerId": "10.8.0.21",
				"listenUrl": "http://10.8.0.21:8080",
				"webSocketUrl": "ws://10.8.0.21:8080/api/internal/platform/opencode-runtime/manager/ws",
				"lastHeartbeatAt": "2026-06-24T00:00:00Z"
			}],
			"traceId": "trace_1234567890abcdef"
		}`))
	}))
	defer server.Close()

	client := NewDiscoveryClient(server.URL, "manager-secret", server.Client())

	backends, err := client.Discover(context.Background())
	if err != nil {
		t.Fatalf("Discover returned error: %v", err)
	}
	if len(backends) != 1 || backends[0].BackendProcessID != "bjp_1234567890abcdef" {
		t.Fatalf("unexpected backends: %#v", backends)
	}
}
