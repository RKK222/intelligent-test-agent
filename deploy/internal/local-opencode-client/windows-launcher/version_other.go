//go:build !windows

package main

import "errors"

func ensureSupportedWindows() (uint32, error) {
	return 0, errors.New("Win10 安装器只能在 Windows 上运行")
}
