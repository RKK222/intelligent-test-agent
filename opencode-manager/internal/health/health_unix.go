//go:build !windows

package health

import (
	"os"
	"syscall"
)

// DefaultProcessAlive 使用 signal 0 判断 Unix 进程是否仍存在。
func DefaultProcessAlive(pid int) bool {
	process, err := os.FindProcess(pid)
	if err != nil {
		return false
	}
	return process.Signal(syscall.Signal(0)) == nil
}
