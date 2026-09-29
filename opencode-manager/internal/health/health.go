package health

import (
	"context"
	"encoding/base64"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/enterprise/test-agent/opencode-manager/internal/state"
)

// Status 表达 opencode server 的本地健康检测结果。
type Status string

const (
	StatusHealthy   Status = "HEALTHY"
	StatusUnhealthy Status = "UNHEALTHY"
)

// Result 是 health 子命令和进程管理库共用的健康结果。
type Result struct {
	Status  Status `json:"status"`
	Port    int    `json:"port"`
	PID     int    `json:"pid"`
	Message string `json:"message"`
	TraceID string `json:"traceId"`
}

// Checker 先检查 PID，再探测 opencode HTTP 健康端点。
type Checker struct {
	Client       *http.Client
	ProcessAlive func(pid int) bool
	Timeout      time.Duration
	ProbeBaseURL string
	// Password 是 V2 server 的 Basic Auth 密码；为空时读取 worker 环境变量。
	Password string
}

// Check 执行本地 PID、HTTP 存活和配置可加载检查。
// /api/info 只证明 V2 服务存活；/api/config 用于确认公共配置符合当前
// OpenCode 原生 schema，避免进程绿灯但 command/skill 等运行时接口全部返回 400。
func (c Checker) Check(ctx context.Context, record state.ProcessRecord) Result {
	alive := c.ProcessAlive
	if alive == nil {
		alive = DefaultProcessAlive
	}
	if !alive(record.PID) {
		return Result{
			Status:  StatusUnhealthy,
			Port:    record.Port,
			PID:     record.PID,
			Message: "process is not alive",
			TraceID: record.TraceID,
		}
	}

	client := c.Client
	if client == nil {
		client = http.DefaultClient
	}
	timeout := c.Timeout
	if timeout <= 0 {
		timeout = 2 * time.Second
	}
	checkCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	baseURL := c.probeBaseURL(record)
	password := strings.TrimSpace(c.Password)
	if password == "" {
		password = strings.TrimSpace(os.Getenv("TEST_AGENT_OPENCODE_SERVER_PASSWORD"))
		if password == "" {
			password = strings.TrimSpace(os.Getenv("OPENCODE_PASSWORD"))
		}
	}
	if ok, _ := httpOK(checkCtx, client, baseURL, "/api/info", password); !ok {
		return Result{
			Status:  StatusUnhealthy,
			Port:    record.Port,
			PID:     record.PID,
			Message: "opencode /api/info endpoint is not reachable",
			TraceID: record.TraceID,
		}
	}
	if ok, message := httpOK(checkCtx, client, baseURL, "/api/config", password); ok {
		return healthy(record, message)
	}
	return Result{
		Status:  StatusUnhealthy,
		Port:    record.Port,
		PID:     record.PID,
		Message: "opencode configuration is not loadable",
		TraceID: record.TraceID,
	}
}

func (c Checker) probeBaseURL(record state.ProcessRecord) string {
	if strings.TrimSpace(c.ProbeBaseURL) != "" {
		return c.ProbeBaseURL
	}
	return fmt.Sprintf("http://127.0.0.1:%d", record.Port)
}

func healthy(record state.ProcessRecord, message string) Result {
	return Result{
		Status:  StatusHealthy,
		Port:    record.Port,
		PID:     record.PID,
		Message: message,
		TraceID: record.TraceID,
	}
}

func httpOK(ctx context.Context, client *http.Client, baseURL string, path string, password string) (bool, string) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, strings.TrimRight(baseURL, "/")+path, nil)
	if err != nil {
		return false, err.Error()
	}
	if password != "" {
		request.Header.Set("Authorization", "Basic "+base64.StdEncoding.EncodeToString([]byte("opencode:"+password)))
	}
	response, err := client.Do(request)
	if err != nil {
		return false, err.Error()
	}
	defer response.Body.Close()
	if response.StatusCode >= 200 && response.StatusCode < 300 {
		return true, fmt.Sprintf("%s returned %d", path, response.StatusCode)
	}
	return false, fmt.Sprintf("%s returned %d", path, response.StatusCode)
}
