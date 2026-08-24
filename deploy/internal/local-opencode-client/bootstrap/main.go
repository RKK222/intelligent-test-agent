package main

import (
	"bufio"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

var (
	expectedLauncherSHA256 string
	expectedIconSHA256     string
	releaseVersion         string
)

type terminalCommand struct {
	name string
	args []string
}

func main() {
	if err := run(os.Args); err != nil {
		writeErrorLog(err)
		notifyFailure(err)
		fmt.Fprintf(os.Stderr, "TestAgent 本地客户端启动失败：%v\n", err)
		os.Exit(1)
	}
}

// run 只负责校验随包资源并把既有用户级安装脚本放入可见终端执行，不复制下载、验签或接入逻辑。
func run(args []string) error {
	executable, err := os.Executable()
	if err != nil {
		return fmt.Errorf("无法定位启动程序: %w", err)
	}
	executable, err = filepath.EvalSymlinks(executable)
	if err != nil {
		return fmt.Errorf("无法解析启动程序路径: %w", err)
	}
	packageRoot := filepath.Dir(executable)
	launcher := filepath.Join(packageRoot, "resources", "test-agent-local-client")
	icon := filepath.Join(packageRoot, "resources", "radar-bunny.png")
	if err := verifyResource(launcher, expectedLauncherSHA256, true); err != nil {
		return fmt.Errorf("安装资源校验失败: %w", err)
	}
	if err := verifyResource(icon, expectedIconSHA256, false); err != nil {
		return fmt.Errorf("图标资源校验失败: %w", err)
	}

	command := ""
	switchArgs := []string{}
	if len(args) > 1 {
		command = args[1]
	}
	if len(args) > 2 {
		switchArgs = args[2:]
	}
	switch command {
	case "--self-check":
		if len(switchArgs) != 0 {
			return errors.New("--self-check 不接受额外参数")
		}
		fmt.Printf("TestAgent user package ready: release=%s architecture=linux-arm64\n", releaseVersion)
		return nil
	case "--run-in-terminal":
		if len(switchArgs) != 0 {
			return errors.New("--run-in-terminal 不接受额外参数")
		}
		return runInstaller(launcher, icon, true)
	case "":
		if len(switchArgs) != 0 {
			return errors.New("启动参数无效")
		}
	default:
		return fmt.Errorf("不支持的启动参数: %s", command)
	}

	if isCharacterDevice(os.Stdin) && isCharacterDevice(os.Stdout) {
		return runInstaller(launcher, icon, false)
	}
	return launchTerminal(executable)
}

func verifyResource(path string, expectedDigest string, requireExecutable bool) error {
	if len(expectedDigest) != 64 {
		return errors.New("包内未固化有效摘要")
	}
	if _, err := hex.DecodeString(expectedDigest); err != nil {
		return errors.New("包内摘要格式无效")
	}
	info, err := os.Lstat(path)
	if err != nil {
		return err
	}
	if !info.Mode().IsRegular() || info.Mode()&os.ModeSymlink != 0 {
		return errors.New("资源必须是普通文件且不能是符号链接")
	}
	if requireExecutable && info.Mode().Perm()&0o111 == 0 {
		return errors.New("安装资源缺少执行权限，请重新解压用户包")
	}
	file, err := os.Open(path)
	if err != nil {
		return err
	}
	defer file.Close()
	digest := sha256.New()
	if _, err := io.Copy(digest, file); err != nil {
		return err
	}
	if hex.EncodeToString(digest.Sum(nil)) != strings.ToLower(expectedDigest) {
		return errors.New("资源 SHA-256 不一致，请重新下载用户包")
	}
	return nil
}

func isCharacterDevice(file *os.File) bool {
	info, err := file.Stat()
	return err == nil && info.Mode()&os.ModeCharDevice != 0
}

// terminalCommands 覆盖麒麟 UKUI 及常见 Linux 桌面终端，参数均以数组传递，禁止拼接 shell 命令。
func terminalCommands(executable string) []terminalCommand {
	return []terminalCommand{
		{name: "x-terminal-emulator", args: []string{"-e", executable, "--run-in-terminal"}},
		{name: "ukui-terminal", args: []string{"-e", executable, "--run-in-terminal"}},
		{name: "mate-terminal", args: []string{"--", executable, "--run-in-terminal"}},
		{name: "gnome-terminal", args: []string{"--", executable, "--run-in-terminal"}},
		{name: "konsole", args: []string{"-e", executable, "--run-in-terminal"}},
		{name: "xfce4-terminal", args: []string{"-x", executable, "--run-in-terminal"}},
		{name: "xterm", args: []string{"-e", executable, "--run-in-terminal"}},
	}
}

func launchTerminal(executable string) error {
	var failures []string
	for _, candidate := range terminalCommands(executable) {
		path, err := exec.LookPath(candidate.name)
		if err != nil {
			continue
		}
		command := exec.Command(path, candidate.args...)
		if err := command.Start(); err == nil {
			return nil
		} else {
			failures = append(failures, candidate.name+": "+err.Error())
		}
	}
	if len(failures) > 0 {
		return fmt.Errorf("无法打开桌面终端（%s）", strings.Join(failures, "; "))
	}
	return errors.New("未找到可用桌面终端，请在当前目录打开终端后运行 ./TestAgent-Local-Client")
}

func runInstaller(launcher string, icon string, pause bool) error {
	command := exec.Command("/bin/sh", launcher, "setup")
	command.Stdin = os.Stdin
	command.Stdout = os.Stdout
	command.Stderr = os.Stderr
	err := command.Run()
	if err == nil {
		if iconErr := installUserIcon(icon); iconErr != nil {
			err = fmt.Errorf("客户端已接入，但桌面图标安装失败: %w", iconErr)
		}
	}
	if pause {
		if err == nil {
			fmt.Println("\n安装完成，可以关闭此窗口。按回车键退出。")
		} else {
			fmt.Printf("\n安装未完成：%v\n按回车键退出。\n", err)
		}
		_, _ = bufio.NewReader(os.Stdin).ReadString('\n')
	}
	return err
}

func installUserIcon(source string) error {
	home, err := os.UserHomeDir()
	if err != nil {
		return err
	}
	targetDir := filepath.Join(home, ".local", "share", "icons", "hicolor", "512x512", "apps")
	if err := os.MkdirAll(targetDir, 0o755); err != nil {
		return err
	}
	target := filepath.Join(targetDir, "test-agent-local-client.png")
	temporary, err := os.CreateTemp(targetDir, ".test-agent-local-client-icon.*")
	if err != nil {
		return err
	}
	temporaryPath := temporary.Name()
	defer os.Remove(temporaryPath)
	input, err := os.Open(source)
	if err != nil {
		temporary.Close()
		return err
	}
	_, copyErr := io.Copy(temporary, input)
	closeInputErr := input.Close()
	closeOutputErr := temporary.Close()
	if copyErr != nil {
		return copyErr
	}
	if closeInputErr != nil {
		return closeInputErr
	}
	if closeOutputErr != nil {
		return closeOutputErr
	}
	if err := os.Chmod(temporaryPath, 0o644); err != nil {
		return err
	}
	return os.Rename(temporaryPath, target)
}

func writeErrorLog(cause error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return
	}
	directory := filepath.Join(home, ".local", "state", "testagent", "local-opencode-client")
	if os.MkdirAll(directory, 0o700) != nil {
		return
	}
	message := fmt.Sprintf("launcher release=%s error=%s\n", releaseVersion, cause)
	_ = os.WriteFile(filepath.Join(directory, "launcher-error.log"), []byte(message), 0o600)
}

func notifyFailure(cause error) {
	path, err := exec.LookPath("notify-send")
	if err != nil {
		return
	}
	message := cause.Error()
	if len(message) > 180 {
		message = message[:180]
	}
	_ = exec.Command(path, "TestAgent 本地客户端启动失败", message).Start()
}
