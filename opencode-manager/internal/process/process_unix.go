//go:build !windows

package process

import (
	"context"
	"os"
	"os/exec"
	"syscall"
)

func (OSStarter) Start(_ context.Context, spec StartSpec) (int, error) {
	logFile, err := os.OpenFile(spec.LogPath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	if err != nil {
		return 0, err
	}
	defer logFile.Close()

	command := exec.Command(spec.Command, spec.Args...)
	command.Env = append(os.Environ(), flattenEnv(spec.Env)...)
	command.Stdout = logFile
	command.Stderr = logFile
	command.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	if err := command.Start(); err != nil {
		return 0, err
	}
	// Unix 子进程必须由创建它的 manager 调用 Wait 回收，否则退出后会以 zombie 保留 PID，
	// 使基于 signal 0 的停止确认误判为仍存活；异步等待保持 Start 的非阻塞语义。
	go func() {
		_ = command.Wait()
	}()
	return command.Process.Pid, nil
}

func (OSSignaler) Terminate(pid int) error {
	return signal(pid, syscall.SIGTERM)
}

func (OSSignaler) Kill(pid int) error {
	return signal(pid, syscall.SIGKILL)
}

func signal(pid int, sig syscall.Signal) error {
	if err := syscall.Kill(-pid, sig); err == nil {
		return nil
	}
	process, err := os.FindProcess(pid)
	if err != nil {
		return err
	}
	return process.Signal(sig)
}
