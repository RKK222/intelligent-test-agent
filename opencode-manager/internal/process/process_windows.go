//go:build windows

package process

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
)

func (OSStarter) Start(_ context.Context, spec StartSpec) (int, error) {
	logFile, err := os.OpenFile(spec.LogPath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	if err != nil {
		return 0, err
	}
	defer logFile.Close()

	// Windows 上 os/exec 无法把 .ps1 当作可执行体直接 fork，必须交给 PowerShell 进程承载脚本解释，
	// 否则会得到 `%1 is not a valid Win32 application`。OPENCODE_BIN 配置成 PowerShell 包装脚本时，
	// 这里自动改写为 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <ps1> <args>`。
	commandName, commandArgs := resolveWindowsCommand(spec.Command, spec.Args)
	command := exec.Command(commandName, commandArgs...)
	command.Env = append(os.Environ(), flattenEnv(spec.Env)...)
	command.Stdout = logFile
	command.Stderr = logFile
	command.SysProcAttr = &syscall.SysProcAttr{CreationFlags: syscall.CREATE_NEW_PROCESS_GROUP}
	if err := command.Start(); err != nil {
		return 0, err
	}
	go func() {
		_ = command.Wait()
	}()
	return command.Process.Pid, nil
}

func (OSSignaler) Terminate(pid int) error {
	return signal(pid)
}

func (OSSignaler) Kill(pid int) error {
	return signal(pid)
}

func signal(pid int) error {
	process, err := os.FindProcess(pid)
	if err != nil {
		return err
	}
	return process.Kill()
}

// resolveWindowsCommand 检测 .ps1 包装脚本并改写为 powershell.exe 调用；其它可执行后缀原样返回。
// 大小写不敏感地匹配扩展名，避免手工配置 OPENCODE_BIN 时大写扩展名绕过检测。
func resolveWindowsCommand(command string, args []string) (string, []string) {
	if !strings.EqualFold(filepath.Ext(command), ".ps1") {
		return command, args
	}
	pwshArgs := make([]string, 0, len(args)+4)
	pwshArgs = append(pwshArgs, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", command)
	pwshArgs = append(pwshArgs, args...)
	return "powershell.exe", pwshArgs
}
