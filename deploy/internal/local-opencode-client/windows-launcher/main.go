package main

import (
	"archive/tar"
	"bufio"
	"compress/gzip"
	"crypto"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"
)

const (
	launcherVersion          = 1
	applyExitCode            = 42
	activationFailedExitCode = 43
	minimumWindowsBuild      = 17763 // Windows 10 1809，与 Bun/OpenCode Windows 运行时最低要求一致。
	maxExtractedBytes        = int64(4 * 1024 * 1024 * 1024)
	scheduledTaskName        = "TestAgent Local Client"
)

var (
	releaseVersion       string
	serverURL            string
	webURL               string
	downloadBaseURL      string
	allowInsecureControl string
	publicKeyDERBase64   string
	expectedStableSHA256 string
)

type releaseManifest struct {
	SchemaVersion      int                `json:"schemaVersion"`
	Version            string             `json:"version"`
	Platform           string             `json:"platform"`
	Architecture       string             `json:"architecture"`
	LauncherVersionMin int                `json:"launcherVersionMin"`
	LauncherVersionMax int                `json:"launcherVersionMax"`
	ProtocolVersion    string             `json:"protocolVersion"`
	OpencodeVersion    string             `json:"opencodeVersion"`
	Artifacts          []manifestArtifact `json:"artifacts"`
}

type manifestArtifact struct {
	Kind          string `json:"kind"`
	Path          string `json:"path"`
	Size          int64  `json:"size"`
	SHA256        string `json:"sha256"`
	SignaturePath string `json:"signaturePath"`
}

type layout struct {
	installRoot string
	configDir   string
	stateDir    string
	releasesDir string
	launcher    string
	currentFile string
}

func main() {
	if err := run(os.Args); err != nil {
		launcherEvent("ERROR", "launcher_failed", map[string]any{
			"stage":       diagnostics.currentStage(),
			"failureCode": launcherFailureCode(),
			"errorType":   fmt.Sprintf("%T", err),
		})
		writeErrorLog(err)
		fmt.Fprintf(os.Stderr, "TestAgent 本地客户端失败：%v\n", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if runtime.GOOS != "windows" {
		return errors.New("Win10 安装器只能在 Windows 上运行")
	}
	command := "setup"
	if len(args) > 1 {
		command = args[1]
	}
	if len(args) > 2 {
		return errors.New("启动参数过多")
	}
	paths, err := resolveLayout()
	if err != nil {
		return err
	}
	diagnostics, err = newLauncherDiagnostics(paths)
	if err != nil {
		return fmt.Errorf("初始化启动器日志失败: %w", err)
	}
	setLauncherStage("platform_check")
	build, err := ensureSupportedWindows()
	launcherEvent("INFO", "launcher_platform_checked", map[string]any{
		"windowsBuild": build,
		"minimumBuild": minimumWindowsBuild,
		"supported":    err == nil,
	})
	if err != nil {
		return err
	}
	launcherEvent("INFO", "launcher_command_started", map[string]any{"command": command})
	var commandError error
	switch command {
	case "setup":
		commandError = installFromPackage(paths)
	case "run":
		commandError = runManagedClient(paths)
	case "start":
		setLauncherStage("scheduled_task_start")
		commandError = startScheduledTask()
	case "--self-check":
		fmt.Printf("TestAgent Win10 x64 launcher ready: launcher=%d release=%s\n", launcherVersion, releaseVersion)
	case "--version":
		current, _ := readCurrentVersion(paths)
		fmt.Printf("test-agent-local-client launcher=%d release=%s\n", launcherVersion, current)
	default:
		commandError = errors.New("不支持的命令")
	}
	if commandError == nil {
		launcherEvent("INFO", "launcher_command_completed", map[string]any{"command": command})
	}
	return commandError
}

func resolveLayout() (layout, error) {
	localAppData := strings.TrimSpace(os.Getenv("LOCALAPPDATA"))
	appData := strings.TrimSpace(os.Getenv("APPDATA"))
	if localAppData == "" || appData == "" {
		return layout{}, errors.New("Windows 用户 AppData 目录不可用")
	}
	installRoot := filepath.Join(localAppData, "TestAgent", "local-opencode-client")
	configDir := filepath.Join(appData, "TestAgent", "local-opencode-client")
	stateDir := filepath.Join(installRoot, "state")
	return layout{
		installRoot: installRoot,
		configDir:   configDir,
		stateDir:    stateDir,
		releasesDir: filepath.Join(installRoot, "releases"),
		launcher:    filepath.Join(installRoot, "bin", "TestAgent-Local-Client.exe"),
		currentFile: filepath.Join(installRoot, "current.version"),
	}, nil
}

// installFromPackage 只接受与 Setup.exe 同目录的固定 resources，不从临时目录猜测安装来源。
func installFromPackage(paths layout) error {
	startedAt := time.Now()
	setLauncherStage("package_verification")
	executable, err := os.Executable()
	if err != nil {
		return fmt.Errorf("无法定位安装器: %w", err)
	}
	packageRoot := filepath.Dir(executable)
	resources := filepath.Join(packageRoot, "resources")
	stableSource := filepath.Join(resources, "TestAgent-Local-Client.exe")
	if err := verifyDigest(stableSource, expectedStableSHA256); err != nil {
		return fmt.Errorf("稳定启动器校验失败: %w", err)
	}
	launcherEvent("INFO", "launcher_stable_binary_verified", map[string]any{
		"sha256": expectedStableSHA256,
	})
	manifest, manifestDigest, err := verifyPackagedRelease(resources)
	if err != nil {
		return err
	}
	if manifest.Version != releaseVersion {
		return errors.New("安装器版本与发布清单不一致")
	}
	launcherEvent("INFO", "launcher_manifest_verified", map[string]any{
		"targetVersion":  manifest.Version,
		"manifestDigest": manifestDigest,
		"artifactCount":  len(manifest.Artifacts),
	})
	setLauncherStage("release_install")
	if err := os.MkdirAll(paths.releasesDir, 0o700); err != nil {
		return err
	}
	finalRelease := filepath.Join(paths.releasesDir, manifest.Version)
	if err := installRelease(resources, finalRelease, manifest, manifestDigest); err != nil {
		return err
	}
	launcherEvent("INFO", "launcher_release_ready", map[string]any{"targetVersion": manifest.Version})
	setLauncherStage("stable_launcher_install")
	if err := os.MkdirAll(filepath.Dir(paths.launcher), 0o700); err != nil {
		return err
	}
	if err := copyAtomically(stableSource, paths.launcher); err != nil {
		return fmt.Errorf("安装稳定启动器失败: %w", err)
	}
	launcherEvent("INFO", "launcher_stable_binary_installed", map[string]any{"targetVersion": manifest.Version})
	setLauncherStage("release_switch")
	if err := switchCurrent(paths, manifest.Version); err != nil {
		return err
	}
	launcherEvent("INFO", "launcher_release_switched", map[string]any{"targetVersion": manifest.Version})
	setLauncherStage("enrollment")
	if err := ensureEnrolled(paths, manifest.Version); err != nil {
		return fmt.Errorf("客户端接入未完成: %w", err)
	}
	setLauncherStage("scheduled_task_install")
	if err := installScheduledTask(paths); err != nil {
		return err
	}
	setLauncherStage("start_menu_install")
	if err := installStartMenuShortcut(paths); err != nil {
		return err
	}
	setLauncherStage("scheduled_task_start")
	if err := startScheduledTask(); err != nil {
		return err
	}
	launcherEvent("INFO", "launcher_setup_completed", map[string]any{
		"targetVersion": manifest.Version,
		"durationMs":    time.Since(startedAt).Milliseconds(),
	})
	fmt.Println("Win10 x64 本地客户端已安装并开始连接平台。")
	return nil
}

func verifyPackagedRelease(resources string) (releaseManifest, string, error) {
	manifestPath := filepath.Join(resources, "manifest.json")
	manifestBytes, err := os.ReadFile(manifestPath)
	if err != nil {
		return releaseManifest{}, "", fmt.Errorf("读取发布清单失败: %w", err)
	}
	publicKey, err := signingPublicKey()
	if err != nil {
		return releaseManifest{}, "", err
	}
	if err := verifySignature(publicKey, manifestBytes, filepath.Join(resources, "manifest.json.sig")); err != nil {
		return releaseManifest{}, "", fmt.Errorf("发布清单签名无效: %w", err)
	}
	var manifest releaseManifest
	if err := json.Unmarshal(manifestBytes, &manifest); err != nil {
		return releaseManifest{}, "", errors.New("发布清单 JSON 无效")
	}
	if manifest.SchemaVersion != 2 || manifest.Version == "" || manifest.Platform != "windows" ||
		manifest.Architecture != "x64" || manifest.LauncherVersionMin > launcherVersion ||
		manifest.LauncherVersionMax < launcherVersion || manifest.ProtocolVersion != "local-opencode-client.v1" ||
		manifest.OpencodeVersion != "1.18.4" {
		return releaseManifest{}, "", errors.New("发布清单与 Win10 x64 启动器不兼容")
	}
	if err := verifyArtifactSet(resources, manifest, publicKey); err != nil {
		return releaseManifest{}, "", err
	}
	digest := sha256.Sum256(manifestBytes)
	return manifest, hex.EncodeToString(digest[:]), nil
}

func verifyArtifactSet(release string, manifest releaseManifest, publicKey *rsa.PublicKey) error {
	required := map[string]bool{"CLIENT_JAR": false, "JDK": false, "OPENCODE": false, "PUBLIC_CAPABILITIES": false}
	prefix := "releases/" + manifest.Version + "/"
	for _, artifact := range manifest.Artifacts {
		fileName, ok := artifactFileName(artifact.Kind)
		if !ok || required[artifact.Kind] {
			return errors.New("发布清单包含未知或重复制品")
		}
		if artifact.Path != prefix+fileName || artifact.SignaturePath != artifact.Path+".sig" ||
			artifact.Size < 1 || artifact.Size > maxExtractedBytes || !validDigest(artifact.SHA256) {
			return errors.New("发布制品元数据无效")
		}
		artifactPath := filepath.Join(release, fileName)
		if err := verifyFile(artifactPath, artifact.Size, artifact.SHA256); err != nil {
			return fmt.Errorf("%s 完整性校验失败: %w", artifact.Kind, err)
		}
		if err := verifyFileSignature(publicKey, artifactPath, filepath.Join(release, fileName+".sig")); err != nil {
			return fmt.Errorf("%s 签名无效: %w", artifact.Kind, err)
		}
		required[artifact.Kind] = true
		launcherEvent("INFO", "launcher_artifact_verified", map[string]any{
			"kind": artifact.Kind, "size": artifact.Size, "sha256": artifact.SHA256,
		})
	}
	for kind, present := range required {
		if !present {
			return fmt.Errorf("发布清单缺少 %s", kind)
		}
	}
	return nil
}

func installRelease(resources, finalRelease string, manifest releaseManifest, manifestDigest string) error {
	if info, err := os.Stat(finalRelease); err == nil && info.IsDir() {
		launcherEvent("INFO", "launcher_release_reuse_started", map[string]any{
			"targetVersion": manifest.Version, "source": "installed_release",
		})
		return verifyInstalledRelease(finalRelease, manifest.Version, manifestDigest)
	}
	launcherEvent("INFO", "launcher_release_preparation_started", map[string]any{
		"targetVersion": manifest.Version, "source": "package_resources",
	})
	stagingParent, err := os.MkdirTemp(filepath.Dir(finalRelease), ".setup-")
	if err != nil {
		return err
	}
	defer os.RemoveAll(stagingParent)
	staging := filepath.Join(stagingParent, manifest.Version)
	if err := os.Mkdir(staging, 0o700); err != nil {
		return err
	}
	for _, name := range []string{
		"manifest.json", "manifest.json.sig", "test-agent-local-client.jar", "test-agent-local-client.jar.sig",
		"jdk.tar.gz", "jdk.tar.gz.sig", "opencode.tar.gz", "opencode.tar.gz.sig",
		"public-capabilities.tar.gz", "public-capabilities.tar.gz.sig",
	} {
		if err := copyFile(filepath.Join(resources, name), filepath.Join(staging, name)); err != nil {
			return err
		}
	}
	if err := extractTarGz(filepath.Join(staging, "jdk.tar.gz"), staging, "jdk"); err != nil {
		return fmt.Errorf("解压 JDK 失败: %w", err)
	}
	launcherEvent("INFO", "launcher_archive_extracted", map[string]any{"kind": "JDK"})
	if err := extractTarGz(filepath.Join(staging, "opencode.tar.gz"), staging, "opencode"); err != nil {
		return fmt.Errorf("解压 OpenCode 失败: %w", err)
	}
	launcherEvent("INFO", "launcher_archive_extracted", map[string]any{"kind": "OPENCODE"})
	if err := verifyRuntime(staging, manifest.Version); err != nil {
		return err
	}
	if err := os.Rename(staging, finalRelease); err != nil {
		return fmt.Errorf("发布单元切换失败: %w", err)
	}
	launcherEvent("INFO", "launcher_release_published", map[string]any{"targetVersion": manifest.Version})
	return nil
}

func verifyInstalledRelease(release, version, manifestDigest string) error {
	if err := verifyReleaseManifestDigest(release, manifestDigest); err != nil {
		return fmt.Errorf("已安装同版本发布校验失败: %w", err)
	}
	return verifyRuntime(release, version)
}

func verifyRuntime(release, version string) error {
	startedAt := time.Now()
	launcherEvent("INFO", "launcher_runtime_verification_started", map[string]any{"targetVersion": version})
	java := filepath.Join(release, "jdk", "bin", "java.exe")
	javaw := filepath.Join(release, "jdk", "bin", "javaw.exe")
	javac := filepath.Join(release, "jdk", "bin", "javac.exe")
	opencode := filepath.Join(release, "opencode", "bin", "opencode.exe")
	jar := filepath.Join(release, "test-agent-local-client.jar")
	for _, path := range []string{java, javaw, javac, opencode, jar, filepath.Join(release, "opencode", "plugins", "test-agent-observability.mjs")} {
		if info, err := os.Stat(path); err != nil || !info.Mode().IsRegular() {
			return fmt.Errorf("发布单元缺少文件: %s", filepath.Base(path))
		}
	}
	output, err := exec.Command(javac, "--version").CombinedOutput()
	if err != nil || !strings.HasPrefix(strings.TrimSpace(string(output)), "javac 21") {
		return errors.New("发布单元 JDK 不是 Java 21")
	}
	output, err = exec.Command(opencode, "--version").CombinedOutput()
	if err != nil || strings.TrimSpace(string(output)) != "1.18.4" {
		return errors.New("发布单元 OpenCode 版本不是 1.18.4")
	}
	command := exec.Command(java, "-jar", jar, "self-check", release, version)
	command.Env = append(os.Environ(), "TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR=", "TEST_AGENT_LOCAL_CLIENT_STATE_DIR=")
	if output, err = command.CombinedOutput(); err != nil {
		return errors.New("候选客户端自检失败")
	}
	launcherEvent("INFO", "launcher_runtime_verification_completed", map[string]any{
		"targetVersion": version, "javaMajor": 21, "opencodeVersion": "1.18.4",
		"durationMs": time.Since(startedAt).Milliseconds(),
	})
	return nil
}

func switchCurrent(paths layout, version string) error {
	if !validVersion(version) {
		return errors.New("拒绝切换到非法版本")
	}
	release := filepath.Join(paths.releasesDir, version)
	if _, err := os.Stat(release); err != nil {
		return errors.New("目标发布目录不存在")
	}
	if err := os.MkdirAll(paths.installRoot, 0o700); err != nil {
		return err
	}
	if err := writeAtomically(paths.currentFile, []byte(version+"\n")); err != nil {
		return err
	}
	return writeConfiguration(paths, version)
}

func writeConfiguration(paths layout, version string) error {
	if err := os.MkdirAll(paths.configDir, 0o700); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Join(paths.stateDir, "logs"), 0o700); err != nil {
		return err
	}
	computerName := strings.TrimSpace(os.Getenv("COMPUTERNAME"))
	if computerName == "" {
		computerName = "windows"
	}
	userName := strings.TrimSpace(os.Getenv("USERNAME"))
	if userName == "" {
		userName = "user"
	}
	values := [][2]string{
		{"serverUrl", serverURL}, {"webUrl", webURL}, {"clientName", userName + "@" + computerName},
		{"opencodeExecutable", filepath.Join(paths.releasesDir, version, "opencode", "bin", "opencode.exe")},
		{"opencodeConfigDirectory", filepath.Join(paths.configDir, "opencode-config")},
		{"opencodeDataDirectory", filepath.Join(paths.stateDir, "opencode-data")},
		{"portMin", "4096"}, {"portMax", "4195"}, {"allowInsecureControl", allowInsecureControl},
		{"downloadBaseUrl", downloadBaseURL}, {"signingPublicKeyBase64", publicKeyDERBase64},
		{"installRoot", paths.installRoot},
	}
	var builder strings.Builder
	builder.WriteString("# TestAgent local client configuration\n")
	for _, value := range values {
		builder.WriteString(value[0])
		builder.WriteByte('=')
		builder.WriteString(escapeProperty(value[1]))
		builder.WriteByte('\n')
	}
	return writeAtomically(filepath.Join(paths.configDir, "client.properties"), []byte(builder.String()))
}

func runManagedClient(paths layout) error {
	setLauncherStage("managed_run")
	for {
		current, err := readCurrentVersion(paths)
		if err != nil {
			return err
		}
		pending, pendingErr := readProperties(filepath.Join(paths.stateDir, "pending-update.properties"))
		_, resultErr := os.Stat(filepath.Join(paths.stateDir, "update-result.properties"))
		if pendingErr == nil && os.IsNotExist(resultErr) && pending["targetVersion"] == current {
			launcherEvent("INFO", "launcher_pending_activation_detected", map[string]any{
				"currentVersion": current, "targetVersion": pending["targetVersion"],
			})
			exitCode, err := runActivation(paths, pending)
			if err != nil {
				return err
			}
			if exitCode == 99 {
				continue
			}
			if exitCode != 0 {
				return fmt.Errorf("客户端进程退出码 %d", exitCode)
			}
			return nil
		}
		exitCode, err := runJavaExitCode(paths, current)
		if err != nil {
			return err
		}
		launcherEvent("INFO", "launcher_client_process_exited", map[string]any{
			"activeVersion": current, "exitCode": exitCode,
		})
		switch exitCode {
		case applyExitCode:
			if err := preparePendingSwitch(paths); err != nil {
				return err
			}
		case activationFailedExitCode:
			if err := rollbackPending(paths, "TARGET_ACTIVATION_FAILED"); err != nil {
				return err
			}
		default:
			if exitCode != 0 {
				return fmt.Errorf("客户端进程退出码 %d", exitCode)
			}
			return nil
		}
	}
}

func preparePendingSwitch(paths layout) error {
	setLauncherStage("update_switch")
	pending, err := validPending(paths)
	if err != nil {
		return err
	}
	current, err := readCurrentVersion(paths)
	if err != nil || current != pending["currentVersion"] {
		return errors.New("更新 marker 的当前版本已失效")
	}
	if err := verifyReleaseManifestDigest(filepath.Join(paths.releasesDir, pending["targetVersion"]), pending["releaseDigest"]); err != nil {
		return err
	}
	_ = os.Remove(filepath.Join(paths.stateDir, "update-activation.properties"))
	_ = os.Remove(filepath.Join(paths.stateDir, "update-result.properties"))
	if err := switchCurrent(paths, pending["targetVersion"]); err != nil {
		return err
	}
	launcherEvent("INFO", "launcher_update_switched", map[string]any{
		"currentVersion": pending["currentVersion"], "targetVersion": pending["targetVersion"],
		"direction": pending["direction"],
	})
	return nil
}

func runActivation(paths layout, pending map[string]string) (int, error) {
	setLauncherStage("update_activation")
	version := pending["targetVersion"]
	startedAt := time.Now()
	launcherEvent("INFO", "launcher_activation_started", map[string]any{
		"targetVersion": version, "timeoutSeconds": 70,
	})
	command, err := javaCommand(paths, version, true)
	if err != nil {
		return 0, err
	}
	if err := command.Start(); err != nil {
		return 0, err
	}
	processDone := make(chan error, 1)
	go func() { processDone <- command.Wait() }()
	deadline := time.Now().Add(70 * time.Second)
	for time.Now().Before(deadline) {
		select {
		case <-processDone:
			_ = rollbackPending(paths, "TARGET_ACTIVATION_FAILED")
			launcherEvent("WARN", "launcher_activation_failed", map[string]any{
				"targetVersion": version, "failureCode": "TARGET_ACTIVATION_FAILED",
				"durationMs": time.Since(startedAt).Milliseconds(),
			})
			return 99, nil
		default:
		}
		activation, err := readProperties(filepath.Join(paths.stateDir, "update-activation.properties"))
		if err == nil {
			if activation["actualVersion"] != version {
				_ = command.Process.Kill()
				return 0, errors.New("激活 marker 版本不一致")
			}
			switch activation["status"] {
			case "READY":
				if err := writeUpdateResult(paths, pending, "SUCCEEDED", "", version); err != nil {
					_ = command.Process.Kill()
					return 0, err
				}
				err := <-processDone
				launcherEvent("INFO", "launcher_activation_ready", map[string]any{
					"targetVersion": version, "durationMs": time.Since(startedAt).Milliseconds(),
				})
				return processExitCode(err), nil
			case "FAILED":
				_ = command.Process.Kill()
				<-processDone
				_ = rollbackPending(paths, firstNonEmpty(activation["errorCode"], "TARGET_ACTIVATION_FAILED"))
				return 99, nil
			default:
				_ = command.Process.Kill()
				return 0, errors.New("激活 marker 状态无效")
			}
		}
		time.Sleep(time.Second)
	}
	_ = command.Process.Kill()
	<-processDone
	_ = rollbackPending(paths, "TARGET_ACTIVATION_TIMEOUT")
	launcherEvent("WARN", "launcher_activation_failed", map[string]any{
		"targetVersion": version, "failureCode": "TARGET_ACTIVATION_TIMEOUT",
		"durationMs": time.Since(startedAt).Milliseconds(),
	})
	return 99, nil
}

func rollbackPending(paths layout, errorCode string) error {
	setLauncherStage("update_rollback")
	pending, err := validPending(paths)
	if err != nil {
		return err
	}
	if err := verifyReleaseManifestDigest(filepath.Join(paths.releasesDir, pending["currentVersion"]), ""); err != nil {
		return err
	}
	if err := switchCurrent(paths, pending["currentVersion"]); err != nil {
		return err
	}
	if err := writeUpdateResult(paths, pending, "AUTO_ROLLED_BACK", errorCode, pending["currentVersion"]); err != nil {
		return err
	}
	_ = os.Remove(filepath.Join(paths.stateDir, "update-activation.properties"))
	launcherEvent("WARN", "launcher_update_rolled_back", map[string]any{
		"restoredVersion": pending["currentVersion"], "failureCode": errorCode,
	})
	return nil
}

func validPending(paths layout) (map[string]string, error) {
	pending, err := readProperties(filepath.Join(paths.stateDir, "pending-update.properties"))
	if err != nil {
		return nil, errors.New("更新 pending marker 不存在或不可读")
	}
	current := pending["currentVersion"]
	target := pending["targetVersion"]
	direction := pending["direction"]
	if pending["schemaVersion"] != "1" || !validVersion(current) || !validVersion(target) || !validDigest(pending["releaseDigest"]) {
		return nil, errors.New("更新 marker 字段无效")
	}
	if direction == "UPDATE" && target <= current || direction == "ROLLBACK" && target >= current {
		return nil, errors.New("更新 marker 方向无效")
	}
	if direction != "UPDATE" && direction != "ROLLBACK" {
		return nil, errors.New("更新 marker 方向无效")
	}
	return pending, nil
}

func writeUpdateResult(paths layout, pending map[string]string, status, errorCode, actualVersion string) error {
	keys := []string{"schemaVersion", "commandId", "clientInstanceId", "connectionGeneration", "policyRevision", "currentVersion", "targetVersion", "direction", "releaseDigest", "preparedAt"}
	var builder strings.Builder
	for _, key := range keys {
		builder.WriteString(key + "=" + pending[key] + "\n")
	}
	builder.WriteString("status=" + status + "\nerrorCode=" + errorCode + "\nactualVersion=" + actualVersion + "\nobservedAt=" + time.Now().UTC().Format(time.RFC3339) + "\n")
	return writeAtomically(filepath.Join(paths.stateDir, "update-result.properties"), []byte(builder.String()))
}

func verifyReleaseManifestDigest(release, expectedDigest string) error {
	manifestBytes, err := os.ReadFile(filepath.Join(release, "manifest.json"))
	if err != nil {
		return errors.New("目标发布清单不存在")
	}
	digest := sha256.Sum256(manifestBytes)
	actual := hex.EncodeToString(digest[:])
	if expectedDigest != "" && actual != expectedDigest {
		return errors.New("目标发布清单摘要不一致")
	}
	publicKey, err := signingPublicKey()
	if err != nil {
		return err
	}
	if err := verifySignature(publicKey, manifestBytes, filepath.Join(release, "manifest.json.sig")); err != nil {
		return errors.New("目标发布清单签名无效")
	}
	var manifest releaseManifest
	if json.Unmarshal(manifestBytes, &manifest) != nil || manifest.SchemaVersion != 2 ||
		manifest.Version != filepath.Base(release) || manifest.Platform != "windows" || manifest.Architecture != "x64" ||
		manifest.LauncherVersionMin > launcherVersion || manifest.LauncherVersionMax < launcherVersion ||
		manifest.ProtocolVersion != "local-opencode-client.v1" || manifest.OpencodeVersion != "1.18.4" {
		return errors.New("目标发布清单平台不兼容")
	}
	if err := verifyArtifactSet(release, manifest, publicKey); err != nil {
		return fmt.Errorf("目标发布制品校验失败: %w", err)
	}
	return verifyRuntimeFiles(release)
}

func verifyRuntimeFiles(release string) error {
	for _, path := range []string{
		filepath.Join(release, "jdk", "bin", "java.exe"),
		filepath.Join(release, "jdk", "bin", "javaw.exe"),
		filepath.Join(release, "jdk", "bin", "javac.exe"),
		filepath.Join(release, "opencode", "bin", "opencode.exe"),
		filepath.Join(release, "test-agent-local-client.jar"),
	} {
		if info, err := os.Stat(path); err != nil || !info.Mode().IsRegular() {
			return errors.New("目标发布目录不完整")
		}
	}
	return nil
}

func ensureEnrolled(paths layout, version string) error {
	credentials := filepath.Join(paths.configDir, "credentials.properties")
	marker := filepath.Join(paths.stateDir, "re-enrollment-required")
	credentialsPresent, err := regularFileState(credentials)
	if err != nil {
		return fmt.Errorf("客户端凭据文件不安全: %w", err)
	}
	markerPresent, err := regularFileState(marker)
	if err != nil {
		return fmt.Errorf("重新接入标记不安全: %w", err)
	}
	if credentialsPresent && !markerPresent {
		launcherEvent("INFO", "launcher_enrollment_reused", map[string]any{
			"targetVersion": version, "credentialPresent": true,
		})
		return nil
	}
	launcherEvent("INFO", "launcher_enrollment_started", map[string]any{
		"targetVersion": version, "credentialPresent": credentialsPresent,
		"reEnrollmentRequired": markerPresent,
	})
	if err := runJava(paths, version, "enroll"); err != nil {
		return err
	}
	credentialsPresent, err = regularFileState(credentials)
	if err != nil || !credentialsPresent {
		return errors.New("接入成功后未生成安全的凭据文件")
	}
	if err := os.Remove(marker); err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	launcherEvent("INFO", "launcher_enrollment_completed", map[string]any{"targetVersion": version})
	return nil
}

func regularFileState(path string) (bool, error) {
	info, err := os.Lstat(path)
	if errors.Is(err, os.ErrNotExist) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	if info.Mode()&os.ModeSymlink != 0 || !info.Mode().IsRegular() {
		return false, errors.New("路径不是普通文件")
	}
	return true, nil
}

func runJava(paths layout, version, argument string) error {
	command, err := javaCommand(paths, version, false, argument)
	if err != nil {
		return err
	}
	command.Stdin = os.Stdin
	command.Stdout = os.Stdout
	command.Stderr = os.Stderr
	return command.Run()
}

func runJavaExitCode(paths layout, version string) (int, error) {
	command, err := javaCommand(paths, version, true)
	if err != nil {
		return 0, err
	}
	command.Stdin = os.Stdin
	command.Stdout = os.Stdout
	command.Stderr = os.Stderr
	launcherEvent("INFO", "launcher_client_process_started", map[string]any{
		"activeVersion": version, "mode": "background",
	})
	err = command.Run()
	return processExitCode(err), nil
}

func javaCommand(paths layout, version string, gui bool, arguments ...string) (*exec.Cmd, error) {
	release := filepath.Join(paths.releasesDir, version)
	if err := verifyRuntimeFiles(release); err != nil {
		return nil, err
	}
	args := []string{"-jar", filepath.Join(release, "test-agent-local-client.jar")}
	args = append(args, arguments...)
	javaExecutable := "java.exe"
	if gui {
		javaExecutable = "javaw.exe"
	}
	command := exec.Command(filepath.Join(release, "jdk", "bin", javaExecutable), args...)
	command.Env = append(os.Environ(),
		"TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR="+paths.configDir,
		"TEST_AGENT_LOCAL_CLIENT_STATE_DIR="+paths.stateDir)
	return command, nil
}

func installScheduledTask(paths layout) error {
	taskCommand := "\"" + paths.launcher + "\" run"
	command := exec.Command("schtasks.exe", "/Create", "/F", "/SC", "ONLOGON", "/RL", "LIMITED", "/IT", "/TN", scheduledTaskName, "/TR", taskCommand)
	if _, err := command.CombinedOutput(); err != nil {
		return errors.New("创建用户级启动任务失败")
	}
	launcherEvent("INFO", "launcher_scheduled_task_installed", map[string]any{"task": scheduledTaskName})
	return nil
}

func startScheduledTask() error {
	command := exec.Command("schtasks.exe", "/Run", "/TN", scheduledTaskName)
	if _, err := command.CombinedOutput(); err != nil {
		return errors.New("启动用户级任务失败")
	}
	launcherEvent("INFO", "launcher_scheduled_task_started", map[string]any{"task": scheduledTaskName})
	return nil
}

func installStartMenuShortcut(paths layout) error {
	programs := filepath.Join(os.Getenv("APPDATA"), "Microsoft", "Windows", "Start Menu", "Programs")
	if err := os.MkdirAll(programs, 0o755); err != nil {
		return err
	}
	shortcut := filepath.Join(programs, "TestAgent 本地客户端.lnk")
	script := `$shell = New-Object -ComObject WScript.Shell; $link = $shell.CreateShortcut($env:TEST_AGENT_SHORTCUT); $link.TargetPath = $env:TEST_AGENT_LAUNCHER; $link.Arguments = 'start'; $link.WorkingDirectory = Split-Path $env:TEST_AGENT_LAUNCHER; $link.IconLocation = $env:TEST_AGENT_LAUNCHER; $link.Save()`
	command := exec.Command("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
	command.Env = append(os.Environ(), "TEST_AGENT_SHORTCUT="+shortcut, "TEST_AGENT_LAUNCHER="+paths.launcher)
	if _, err := command.CombinedOutput(); err != nil {
		return errors.New("创建开始菜单快捷方式失败")
	}
	launcherEvent("INFO", "launcher_start_menu_shortcut_installed", map[string]any{"name": "TestAgent"})
	return nil
}

func signingPublicKey() (*rsa.PublicKey, error) {
	der, err := base64.StdEncoding.DecodeString(publicKeyDERBase64)
	if err != nil {
		return nil, errors.New("安装器内置发布公钥无效")
	}
	parsed, err := x509.ParsePKIXPublicKey(der)
	if err != nil {
		return nil, errors.New("安装器内置发布公钥无法解析")
	}
	publicKey, ok := parsed.(*rsa.PublicKey)
	if !ok || publicKey.Size() < 256 {
		return nil, errors.New("安装器内置发布公钥类型无效")
	}
	return publicKey, nil
}

func verifySignature(publicKey *rsa.PublicKey, content []byte, signaturePath string) error {
	signature, err := os.ReadFile(signaturePath)
	if err != nil {
		return err
	}
	digest := sha256.Sum256(content)
	return rsa.VerifyPKCS1v15(publicKey, crypto.SHA256, digest[:], signature)
}

func verifyFileSignature(publicKey *rsa.PublicKey, contentPath, signaturePath string) error {
	content, err := os.Open(contentPath)
	if err != nil {
		return err
	}
	defer content.Close()
	hash := sha256.New()
	if _, err := io.Copy(hash, content); err != nil {
		return err
	}
	signature, err := os.ReadFile(signaturePath)
	if err != nil {
		return err
	}
	return rsa.VerifyPKCS1v15(publicKey, crypto.SHA256, hash.Sum(nil), signature)
}

func verifyFile(path string, expectedSize int64, expectedDigest string) error {
	info, err := os.Stat(path)
	if err != nil || !info.Mode().IsRegular() || info.Size() != expectedSize {
		return errors.New("文件大小不一致")
	}
	return verifyDigest(path, expectedDigest)
}

func verifyDigest(path, expectedDigest string) error {
	if !validDigest(expectedDigest) {
		return errors.New("预期摘要无效")
	}
	file, err := os.Open(path)
	if err != nil {
		return err
	}
	defer file.Close()
	hash := sha256.New()
	if _, err := io.Copy(hash, file); err != nil {
		return err
	}
	if hex.EncodeToString(hash.Sum(nil)) != strings.ToLower(expectedDigest) {
		return errors.New("SHA-256 不一致")
	}
	return nil
}

func extractTarGz(archive, destination, expectedRoot string) error {
	file, err := os.Open(archive)
	if err != nil {
		return err
	}
	defer file.Close()
	gzipReader, err := gzip.NewReader(file)
	if err != nil {
		return err
	}
	defer gzipReader.Close()
	reader := tar.NewReader(gzipReader)
	var total int64
	for {
		header, err := reader.Next()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return err
		}
		name := filepath.FromSlash(header.Name)
		clean := filepath.Clean(name)
		if filepath.IsAbs(clean) || clean == "." || clean == ".." || strings.HasPrefix(clean, ".."+string(os.PathSeparator)) {
			return errors.New("归档包含越界路径")
		}
		parts := strings.Split(filepath.ToSlash(clean), "/")
		if len(parts) == 0 || parts[0] != expectedRoot {
			return errors.New("归档根目录无效")
		}
		target := filepath.Join(destination, clean)
		if !strings.HasPrefix(strings.ToLower(target), strings.ToLower(filepath.Clean(destination)+string(os.PathSeparator))) {
			return errors.New("归档目标路径越界")
		}
		switch header.Typeflag {
		case tar.TypeDir:
			if err := os.MkdirAll(target, 0o700); err != nil {
				return err
			}
		case tar.TypeReg, tar.TypeRegA:
			total += header.Size
			if header.Size < 0 || total > maxExtractedBytes {
				return errors.New("归档解压大小超过限制")
			}
			if err := os.MkdirAll(filepath.Dir(target), 0o700); err != nil {
				return err
			}
			output, err := os.OpenFile(target, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
			if err != nil {
				return err
			}
			_, copyErr := io.CopyN(output, reader, header.Size)
			closeErr := output.Close()
			if copyErr != nil {
				return copyErr
			}
			if closeErr != nil {
				return closeErr
			}
		default:
			return errors.New("归档包含不支持的链接或设备条目")
		}
	}
	return nil
}

func copyFile(source, target string) error {
	input, err := os.Open(source)
	if err != nil {
		return err
	}
	defer input.Close()
	output, err := os.OpenFile(target, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	_, copyErr := io.Copy(output, input)
	closeErr := output.Close()
	if copyErr != nil {
		return copyErr
	}
	return closeErr
}

func copyAtomically(source, target string) error {
	temporary, err := os.CreateTemp(filepath.Dir(target), ".launcher-")
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
	return os.Rename(temporaryPath, target)
}

func writeAtomically(target string, content []byte) error {
	if err := os.MkdirAll(filepath.Dir(target), 0o700); err != nil {
		return err
	}
	temporary, err := os.CreateTemp(filepath.Dir(target), ".write-")
	if err != nil {
		return err
	}
	temporaryPath := temporary.Name()
	defer os.Remove(temporaryPath)
	if _, err := temporary.Write(content); err != nil {
		temporary.Close()
		return err
	}
	if err := temporary.Close(); err != nil {
		return err
	}
	return os.Rename(temporaryPath, target)
}

func readCurrentVersion(paths layout) (string, error) {
	content, err := os.ReadFile(paths.currentFile)
	if err != nil {
		return "", errors.New("客户端尚未安装")
	}
	version := strings.TrimSpace(string(content))
	if !validVersion(version) {
		return "", errors.New("current.version 无效")
	}
	return version, nil
}

func readProperties(path string) (map[string]string, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	properties := make(map[string]string)
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "!") {
			continue
		}
		separator := strings.IndexAny(line, "=:")
		if separator < 1 {
			continue
		}
		properties[strings.TrimSpace(line[:separator])] = unescapeProperty(strings.TrimSpace(line[separator+1:]))
	}
	return properties, scanner.Err()
}

func escapeProperty(value string) string {
	return strings.NewReplacer("\\", "\\\\", "\r", "\\r", "\n", "\\n").Replace(value)
}

func unescapeProperty(value string) string {
	var builder strings.Builder
	for index := 0; index < len(value); index++ {
		if value[index] != '\\' || index+1 >= len(value) {
			builder.WriteByte(value[index])
			continue
		}
		index++
		switch value[index] {
		case 'n':
			builder.WriteByte('\n')
		case 'r':
			builder.WriteByte('\r')
		case 't':
			builder.WriteByte('\t')
		case 'f':
			builder.WriteByte('\f')
		default:
			builder.WriteByte(value[index])
		}
	}
	return builder.String()
}

func artifactFileName(kind string) (string, bool) {
	switch kind {
	case "CLIENT_JAR":
		return "test-agent-local-client.jar", true
	case "JDK":
		return "jdk.tar.gz", true
	case "OPENCODE":
		return "opencode.tar.gz", true
	case "PUBLIC_CAPABILITIES":
		return "public-capabilities.tar.gz", true
	default:
		return "", false
	}
}

func validVersion(value string) bool {
	if len(value) != 14 {
		return false
	}
	_, err := strconv.ParseInt(value, 10, 64)
	return err == nil
}

func validDigest(value string) bool {
	if len(value) != 64 || strings.ToLower(value) != value {
		return false
	}
	_, err := hex.DecodeString(value)
	return err == nil
}

func processExitCode(err error) int {
	if err == nil {
		return 0
	}
	var exitError *exec.ExitError
	if errors.As(err, &exitError) {
		return exitError.ExitCode()
	}
	return 1
}

func firstNonEmpty(value, fallback string) string {
	if strings.TrimSpace(value) == "" {
		return fallback
	}
	return value
}

func writeErrorLog(cause error) {
	paths, err := resolveLayout()
	if err != nil {
		return
	}
	logsDirectory := filepath.Join(paths.stateDir, "logs")
	if err := os.MkdirAll(logsDirectory, 0o700); err != nil {
		return
	}
	message := fmt.Sprintf("time=%s session=%s launcher=%d release=%s stage=%s failureCode=%s errorType=%T details=see-launcher.log\n",
		time.Now().UTC().Format(time.RFC3339), diagnostics.currentSession(), launcherVersion,
		releaseVersion, diagnostics.currentStage(), launcherFailureCode(), cause)
	_ = writeSafeSummary(filepath.Join(logsDirectory, "windows-launcher-error.log"), []byte(message))
}

// writeSafeSummary 用独占创建替换上一份短摘要，避免跟随用户状态目录中的符号链接。
func writeSafeSummary(path string, content []byte) error {
	if info, err := os.Lstat(path); err == nil {
		if info.Mode()&os.ModeSymlink != 0 || !info.Mode().IsRegular() {
			return errors.New("错误摘要不是安全的普通文件")
		}
		if err := os.Remove(path); err != nil {
			return err
		}
	} else if !errors.Is(err, os.ErrNotExist) {
		return err
	}
	file, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	_, writeErr := file.Write(content)
	closeErr := file.Close()
	if writeErr != nil {
		return writeErr
	}
	return closeErr
}
