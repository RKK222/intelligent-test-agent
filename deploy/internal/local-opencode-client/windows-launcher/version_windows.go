//go:build windows

package main

import (
	"errors"
	"syscall"
	"unsafe"
)

type osVersionInfo struct {
	size        uint32
	major       uint32
	minor       uint32
	build       uint32
	platformID  uint32
	servicePack [128]uint16
}

func ensureSupportedWindows() error {
	info := osVersionInfo{size: uint32(unsafe.Sizeof(osVersionInfo{}))}
	procedure := syscall.NewLazyDLL("ntdll.dll").NewProc("RtlGetVersion")
	result, _, callError := procedure.Call(uintptr(unsafe.Pointer(&info)))
	if result != 0 {
		return callError
	}
	if info.major < 10 || info.major == 10 && info.build < minimumWindowsBuild {
		return errors.New("仅支持 Windows 10 1809（build 17763）及以上 x64 系统")
	}
	return nil
}
