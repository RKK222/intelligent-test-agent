package control

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"
)

// BackendEndpoint 是后端 discovery API 返回的单个可直连后端实例。
type BackendEndpoint struct {
	BackendProcessID string    `json:"backendProcessId"`
	LinuxServerID    string    `json:"linuxServerId"`
	ListenURL        string    `json:"listenUrl"`
	WebSocketURL     string    `json:"webSocketUrl"`
	LastHeartbeatAt  time.Time `json:"lastHeartbeatAt"`
}

// DiscoveryClient 使用独立 manager token 查询所有 READY 后端实例。
type DiscoveryClient struct {
	url        string
	token      string
	httpClient *http.Client
}

// NewDiscoveryClient 创建 discovery API client。
func NewDiscoveryClient(url string, token string, httpClient *http.Client) *DiscoveryClient {
	if httpClient == nil {
		httpClient = http.DefaultClient
	}
	return &DiscoveryClient{url: url, token: token, httpClient: httpClient}
}

// Discover 拉取当前可连接后端列表。
func (c *DiscoveryClient) Discover(ctx context.Context) ([]BackendEndpoint, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, c.url, nil)
	if err != nil {
		return nil, err
	}
	request.Header.Set("Authorization", "Bearer "+c.token)
	response, err := c.httpClient.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return nil, fmt.Errorf("backend discovery returned status %d", response.StatusCode)
	}
	var payload apiResponse
	if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
		return nil, err
	}
	if !payload.Success {
		return nil, fmt.Errorf("backend discovery failed")
	}
	return payload.Data, nil
}

type apiResponse struct {
	Success bool              `json:"success"`
	Data    []BackendEndpoint `json:"data"`
	TraceID string            `json:"traceId"`
}
