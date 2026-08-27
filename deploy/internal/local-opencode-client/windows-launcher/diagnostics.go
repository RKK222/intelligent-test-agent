package main

import (
	cryptorand "crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

const launcherLogMaxBytes = int64(5 * 1024 * 1024)

var diagnostics *launcherDiagnostics

// launcherDiagnostics 为 Setup 与稳定启动器提供同一份按行结构化日志，不引入外部日志依赖。
type launcherDiagnostics struct {
	mu      sync.Mutex
	path    string
	stage   string
	session string
}

// newLauncherDiagnostics 只在当前用户状态目录创建常规文件；符号链接日志会失败关闭。
func newLauncherDiagnostics(paths layout) (*launcherDiagnostics, error) {
	logDirectory := filepath.Join(paths.stateDir, "logs")
	if err := os.MkdirAll(logDirectory, 0o700); err != nil {
		return nil, err
	}
	logPath := filepath.Join(logDirectory, "launcher.log")
	if info, err := os.Lstat(logPath); err == nil {
		if info.Mode()&os.ModeSymlink != 0 || !info.Mode().IsRegular() {
			return nil, errors.New("启动器日志不是安全的普通文件")
		}
		if info.Size() >= launcherLogMaxBytes {
			previous := filepath.Join(logDirectory, "launcher-1.log")
			_ = os.Remove(previous)
			if err := os.Rename(logPath, previous); err != nil {
				return nil, err
			}
		}
	} else if !errors.Is(err, os.ErrNotExist) {
		return nil, err
	}
	file, err := os.OpenFile(logPath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
	if err != nil {
		return nil, err
	}
	if err := file.Close(); err != nil {
		return nil, err
	}
	random := make([]byte, 16)
	if _, err := cryptorand.Read(random); err != nil {
		return nil, err
	}
	return &launcherDiagnostics{
		path:    logPath,
		stage:   "initialization",
		session: "trace_launcher_" + hex.EncodeToString(random),
	}, nil
}

func (logger *launcherDiagnostics) setStage(stage string) {
	logger.mu.Lock()
	logger.stage = safeLauncherLogValue(stage)
	logger.mu.Unlock()
	logger.event("INFO", "launcher_stage_started", map[string]any{"stage": stage})
}

// event 的字段全部由启动器生成；敏感字段名仍会强制脱敏，换行和超长值也不会破坏单行结构。
func (logger *launcherDiagnostics) event(level, event string, fields map[string]any) {
	if logger == nil {
		return
	}
	logger.mu.Lock()
	defer logger.mu.Unlock()
	keys := make([]string, 0, len(fields))
	for key := range fields {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	var line strings.Builder
	fmt.Fprintf(&line, "time=%s level=%s session=%s launcher=%d release=%s platform=windows architecture=x64 event=%s",
		time.Now().UTC().Format(time.RFC3339Nano), safeLauncherLogValue(level), logger.session,
		launcherVersion, safeLauncherLogValue(releaseVersion), safeLauncherLogValue(event))
	for _, key := range keys {
		value := safeLauncherLogValue(fmt.Sprint(fields[key]))
		if sensitiveLauncherLogField(key) {
			value = "REDACTED"
		}
		fmt.Fprintf(&line, " %s=%s", safeLauncherLogValue(key), value)
	}
	line.WriteByte('\n')
	file, err := os.OpenFile(logger.path, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
	if err != nil {
		return
	}
	_, _ = file.WriteString(line.String())
	_ = file.Close()
}

func (logger *launcherDiagnostics) currentStage() string {
	if logger == nil {
		return "initialization"
	}
	logger.mu.Lock()
	defer logger.mu.Unlock()
	return logger.stage
}

func (logger *launcherDiagnostics) currentSession() string {
	if logger == nil {
		return "-"
	}
	return logger.session
}

func setLauncherStage(stage string) {
	if diagnostics != nil {
		diagnostics.setStage(stage)
	}
}

func launcherEvent(level, event string, fields map[string]any) {
	if diagnostics != nil {
		diagnostics.event(level, event, fields)
	}
}

func safeLauncherLogValue(value string) string {
	value = strings.TrimSpace(strings.NewReplacer("\r", "_", "\n", "_", "\t", "_").Replace(value))
	if value == "" {
		return "-"
	}
	if len(value) > 256 {
		return value[:256] + "..."
	}
	return strings.ReplaceAll(value, " ", "_")
}

func sensitiveLauncherLogField(name string) bool {
	normalized := strings.ToLower(name)
	for _, word := range []string{"token", "key", "credential", "authorization", "cookie", "secret", "username", "userid"} {
		if strings.Contains(normalized, word) {
			return true
		}
	}
	return false
}

func launcherFailureCode() string {
	stage := "initialization"
	if diagnostics != nil {
		stage = diagnostics.currentStage()
	}
	return strings.ToUpper(strings.ReplaceAll(safeLauncherLogValue(stage), "-", "_")) + "_FAILED"
}
