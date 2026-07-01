//go:build windows

package health

import (
	"syscall"
	"unsafe"
)

var (
	kernel32                    = syscall.NewLazyDLL("kernel32.dll")
	procOpenProcess             = kernel32.NewProc("OpenProcess")
	procWaitForSingleObject     = kernel32.NewProc("WaitForSingleObject")
	procGetExitCodeProcess      = kernel32.NewProc("GetExitCodeProcess")
	procCloseHandle             = kernel32.NewProc("CloseHandle")
)

const (
	processQueryInformation = 0x0400
	waitTimeout             = 0x00000102
	waitFailed              = 0xFFFFFFFF
)

// DefaultProcessAlive 在 Windows 上通过 OpenProcess + WaitForSingleObject 判断进程是否仍存在。
func DefaultProcessAlive(pid int) bool {
	if pid <= 0 {
		return false
	}
	handle, _, _ := procOpenProcess.Call(uintptr(processQueryInformation), uintptr(0), uintptr(pid))
	if handle == 0 {
		return false
	}
	defer procCloseHandle.Call(handle)

	var exitCode uint32
	result, _, _ := procGetExitCodeProcess.Call(handle, uintptr(unsafe.Pointer(&exitCode)))
	if result == 0 {
		return false
	}
	// STILL_ACTIVE == 259 表示进程仍在运行
	return exitCode == 259
}
