package process

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"path/filepath"
	"strings"
	"time"

	"github.com/enterprise/test-agent/opencode-manager/internal/config"
)

const (
	logTimestampLayout        = "20060102T150405.000000000Z"
	maxEncodedLogIdentity     = 160
	readableLogIdentityPrefix = 80
)

// resolveUnifiedAuthID 优先使用控制面显式身份，并校验其与稳定 users/{id} 会话目录一致。
// 错误信息不包含原始认证号，避免身份信息进入 manager 生命周期日志。
func resolveUnifiedAuthID(explicit, sessionPath string) (string, error) {
	explicit = strings.TrimSpace(explicit)
	derived := unifiedAuthIDFromSessionPath(sessionPath)
	if explicit != "" && derived != "" && explicit != derived {
		return "", fmt.Errorf("unifiedAuthId does not match session path")
	}
	if explicit != "" {
		return explicit, nil
	}
	return derived, nil
}

func unifiedAuthIDFromSessionPath(sessionPath string) string {
	cleaned := filepath.Clean(strings.TrimSpace(sessionPath))
	if cleaned == "." || filepath.Base(filepath.Dir(cleaned)) != "users" {
		return ""
	}
	identity := strings.TrimSpace(filepath.Base(cleaned))
	if identity == "" || identity == "." || identity == ".." {
		return ""
	}
	return identity
}

// safeLogIdentity 仅保留文件名安全的 ASCII 字节，其余 UTF-8 字节用 %HH 编码。
// 超长身份保留可读前缀并附完整 SHA-256，兼顾文件名上限与不同身份不碰撞。
func safeLogIdentity(identity string) string {
	parts := make([]string, 0, len(identity))
	for _, value := range []byte(identity) {
		if isSafeLogIdentityByte(value) {
			parts = append(parts, string([]byte{value}))
		} else {
			parts = append(parts, fmt.Sprintf("%%%02X", value))
		}
	}
	encoded := strings.Join(parts, "")
	if len(encoded) <= maxEncodedLogIdentity {
		return encoded
	}
	var prefix strings.Builder
	for _, part := range parts {
		if prefix.Len()+len(part) > readableLogIdentityPrefix {
			break
		}
		prefix.WriteString(part)
	}
	digest := sha256.Sum256([]byte(identity))
	return prefix.String() + "-sha256-" + hex.EncodeToString(digest[:])
}

func isSafeLogIdentityByte(value byte) bool {
	return value >= 'a' && value <= 'z' ||
		value >= 'A' && value <= 'Z' ||
		value >= '0' && value <= '9' ||
		value == '_' || value == '-'
}

func processLogPath(cfg config.Config, unifiedAuthID string, startedAt time.Time, port int) string {
	if unifiedAuthID == "" {
		// 本地 CLI 或旧调用无法可靠恢复用户身份时，继续使用历史端口日志名。
		return cfg.LogPath(port)
	}
	fileName := fmt.Sprintf("%s-%s-%d.log",
		safeLogIdentity(unifiedAuthID),
		startedAt.Format(logTimestampLayout),
		port)
	return filepath.Join(cfg.StateDir, "logs", fileName)
}
