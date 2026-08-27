//go:build !windows

package main

import "errors"

func ensureSupportedWindows() error {
	return errors.New("Win10 安装器只能在 Windows 上运行")
}
