//go:build windows

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
