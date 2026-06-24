package health

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"strings"
	"syscall"
	"time"

	"github.com/icbc/test-agent/opencode-manager/internal/state"
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
}

// Check 执行本地 PID + HTTP 健康检查，/global/health 失败时回退 /doc。
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
	if ok, message := httpOK(checkCtx, client, baseURL, "/global/health"); ok {
		return healthy(record, message)
	}
	if ok, message := httpOK(checkCtx, client, baseURL, "/doc"); ok {
		return healthy(record, message)
	}
	return Result{
		Status:  StatusUnhealthy,
		Port:    record.Port,
		PID:     record.PID,
		Message: "opencode health endpoints are not reachable",
		TraceID: record.TraceID,
	}
}

func (c Checker) probeBaseURL(record state.ProcessRecord) string {
	if strings.TrimSpace(c.ProbeBaseURL) != "" {
		return c.ProbeBaseURL
	}
	return fmt.Sprintf("http://127.0.0.1:%d", record.Port)
}

// DefaultProcessAlive 使用 signal 0 判断 Unix 进程是否仍存在。
func DefaultProcessAlive(pid int) bool {
	process, err := os.FindProcess(pid)
	if err != nil {
		return false
	}
	return process.Signal(syscall.Signal(0)) == nil
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

func httpOK(ctx context.Context, client *http.Client, baseURL string, path string) (bool, string) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, strings.TrimRight(baseURL, "/")+path, nil)
	if err != nil {
		return false, err.Error()
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
