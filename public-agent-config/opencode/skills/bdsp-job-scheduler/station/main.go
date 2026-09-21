package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strings"
	"syscall"
	"time"
)

const (
	proxyPort      = 8899
	targetAPIPath  = "/api/user/queryUserInfo"
	configName     = "config.txt"
	resultName     = "result.txt"

	defaultPageURL = "http://dataops.sdc.icbc"

	// 静默超时: queryUserInfo 请求间隔超过此时长则认为页面已稳定, 触发结束。
	silenceTimeout = 5 * time.Second
)

var (
	targetURL         string
	exeDir            string
	safariMode        bool
	windowsProxyMode  bool  // IE-based browsers (360 Safe Browser) need system proxy
	linuxProxyMode   bool  // Linux fallback needs system proxy
	netService        string
	browserCmd        *exec.Cmd
	browserProfileDir string
	pacFilePath       string  // PAC 文件路径 (Linux 奇安信浏览器用)
	capturedCh        = make(chan struct{}, 1)
	// 持续抓取: 每次收到 queryUserInfo 都更新 result.txt 并刷新时间戳,
	// 静默超时后才触发结束, 确保拿到的是最后一次有效的 Authorization。
	lastCaptureTime time.Time
	captureCount    int
	captureDone     bool
	// 无头浏览器模式: 首次使用无头模式, 15 秒内无代理流量则自动降级为有头模式重试
	headlessArgs        []string      // 非空 = 无头模式, 空 = 有头模式
	proxyTrafficReceived bool         // 代理是否收到过任何请求
	browserLaunchTime    time.Time    // 浏览器启动时间
	headlessFallbackDone bool         // 是否已执行过降级重试
)

// browserArgs 将无头参数和常规参数组合成完整命令行参数列表
func browserArgs(flags ...string) []string {
	return append(append([]string{}, headlessArgs...), flags...)
}

func main() {
	// 创建日志文件，同时输出到终端和文件（终端关闭后仍可查看日志）
	if exe, err := os.Executable(); err == nil {
		logPath := filepath.Join(filepath.Dir(exe), "capture-proxy.log")
		if logFile, err := os.OpenFile(logPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0644); err == nil {
			origStdout := os.Stdout
			r, w, _ := os.Pipe()
			os.Stdout = w
			os.Stderr = w
			go func() {
				buf := make([]byte, 4096)
				for {
					n, err := r.Read(buf)
					if n > 0 {
						origStdout.Write(buf[:n])
						logFile.Write(buf[:n])
					}
					if err != nil {
						break
					}
				}
				logFile.Close()
			}()
			defer func() {
				w.Close()
				time.Sleep(200 * time.Millisecond)
			}()
		}
	}

	// Global panic recovery
	defer func() {
		if r := recover(); r != nil {
			fmt.Println()
			fmt.Println("==================================================")
			fmt.Printf("[FATAL] Unexpected error: %v\n", r)
			fmt.Println("==================================================")
			cleanupBrowser()
			fmt.Println()
			fmt.Println("Press Enter to exit...")
			bufio.NewReader(os.Stdin).ReadString('\n')
		}
	}()

	fmt.Println("==================================================")
	fmt.Println("          UserInfo Capture Proxy")
	fmt.Println("==================================================")
	fmt.Printf("  Target page:   %s\n", targetURL)
	fmt.Printf("  Proxy listen:  127.0.0.1:%d\n", proxyPort)
	fmt.Printf("  Capture API:   %s\n", targetAPIPath)
	fmt.Printf("  Platform:      %s/%s\n", runtime.GOOS, runtime.GOARCH)
	fmt.Println("==================================================")
	fmt.Println()

	fmt.Println("[1/5] Reading configuration...")
	targetURL = readConfig()
	fmt.Printf("      Target URL: %s\n", targetURL)

	fmt.Println("[2/5] Determining executable directory...")
	exeDir = getExecutableDir()
	fmt.Printf("      Directory: %s\n", exeDir)

	fmt.Println("[3/5] Starting proxy server...")
	server := &http.Server{
		Addr:         fmt.Sprintf("127.0.0.1:%d", proxyPort),
		Handler:      http.HandlerFunc(handleProxyRequest),
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 30 * time.Second,
	}

	serverErr := make(chan error, 1)
	go func() {
		defer func() {
			if r := recover(); r != nil {
				serverErr <- fmt.Errorf("proxy server panic: %v", r)
			}
		}()
		err := server.ListenAndServe()
		if err != nil && err != http.ErrServerClosed {
			serverErr <- err
		}
	}()

	time.Sleep(800 * time.Millisecond)

	select {
	case err := <-serverErr:
		fmt.Println()
		fmt.Println("==================================================")
		fmt.Println("[ERROR] Failed to start proxy server!")
		fmt.Printf("  Reason: %v\n", err)
		fmt.Println("--------------------------------------------------")
		fmt.Println("Common causes:")
		fmt.Println("  1. Port 8899 is already in use")
		fmt.Println("  2. Firewall / antivirus is blocking the port")
		fmt.Println("  3. Try running as administrator")
		fmt.Println("==================================================")
		fmt.Println()
		fmt.Println("Press Enter to exit...")
		bufio.NewReader(os.Stdin).ReadString('\n')
		return
	default:
		fmt.Printf("      Proxy server running on 127.0.0.1:%d\n", proxyPort)
	}

	// 非无头模式: 直接打开浏览器窗口, 抓完后关闭
	headlessArgs = nil
	fmt.Println("[4/4] Launching browser...")
	go launchBrowserSafe()
	go silenceWatcher()

	fmt.Println()
	fmt.Println("==================================================")
	fmt.Println("[INFO] Proxy is running. Capture is active.")
	fmt.Println("  - Each queryUserInfo request updates the captured Authorization.")
	fmt.Println("  - Finishes when no new queryUserInfo for 5s (keeps the last valid one).")
	fmt.Println("  - After finish: browser & proxy close, console exits.")
	fmt.Println("  - Press 'q' + Enter (or Ctrl+C) to exit manually.")
	fmt.Println("==================================================")
	fmt.Println()

	// Wait for exit signal.
	// All platforms: read stdin for 'q' + Enter.
	// Unix: also listen for Ctrl+C (SIGINT).
	exitCh := make(chan struct{}, 1)

	// stdin reader goroutine: reads 'q' + Enter to exit.
	// If stdin is closed (EOF), just stop reading - don't exit.
	// This allows the program to run in background (nohup, etc.)
	go func() {
		defer func() { recover() }()
		reader := bufio.NewReader(os.Stdin)
		for {
			line, err := reader.ReadString('\n')
			if err != nil {
				// stdin closed (EOF or error) - stop reading, wait for signal
				return
			}
			if strings.TrimSpace(strings.ToLower(line)) == "q" {
				exitCh <- struct{}{}
				return
			}
		}
	}()

	// Signal listener (Unix only; Windows signal handling can panic)
	sigCh := make(chan os.Signal, 1)
	if runtime.GOOS != "windows" {
		signal.Notify(sigCh, os.Interrupt, syscall.SIGTERM)
	}

	// 抓取成功后: 关闭带代理的无头浏览器 -> 恢复系统代理 -> 程序退出(控制台关闭)
	// 用户按 q + Enter 或 Ctrl+C: 关闭浏览器并退出
	select {
	case <-capturedCh:
		fmt.Println("\n[INFO] Authorization captured!")
		fmt.Println("[INFO] Closing proxy & console...")
		cleanupBrowser()
	case <-exitCh:
		fmt.Println("\n[INFO] Exit requested by user.")
		cleanupBrowser()
	case <-sigCh:
		fmt.Println("\n[INFO] Exit requested by signal (Ctrl+C).")
		cleanupBrowser()
	case <-serverErr:
		fmt.Println("\n[INFO] Proxy server stopped.")
		cleanupBrowser()
	}

	fmt.Println("[INFO] Goodbye!")
}

// cleanupBrowser closes the launched browser and restores system proxy.
func cleanupBrowser() {
	// Restore Safari system proxy (macOS)
	if safariMode && runtime.GOOS == "darwin" && netService != "" {
		fmt.Println("[INFO] Restoring system proxy settings...")
		exec.Command("networksetup", "-setwebproxystate", netService, "off").Run()
		exec.Command("networksetup", "-setsecurewebproxystate", netService, "off").Run()
		fmt.Println("[INFO] System proxy restored.")
	}
	// Restore Windows system proxy (for IE-based browsers like 360 Safe Browser)
	if windowsProxyMode && runtime.GOOS == "windows" {
		fmt.Println("[INFO] Restoring Windows system proxy settings...")
		restoreWindowsSystemProxy()
		fmt.Println("[INFO] Windows system proxy restored.")
	}
	// Restore Linux system proxy (for browsers found via fallback)
	if linuxProxyMode && runtime.GOOS == "linux" {
		fmt.Println("[INFO] Restoring Linux system proxy settings...")
		restoreLinuxSystemProxy()
		fmt.Println("[INFO] Linux system proxy restored.")
	}
	// Linux 保险: 无条件恢复 gsettings 代理 (旧版本残留 / 奇安信 --proxy-server 副作用)
	if runtime.GOOS == "linux" {
		restoreLinuxSystemProxy()
	}

	// Close the browser
	if browserCmd != nil && browserCmd.Process != nil {
		fmt.Println("[INFO] Closing browser...")
		pid := browserCmd.Process.Pid
		browserExe := filepath.Base(browserCmd.Path)

		if runtime.GOOS == "windows" {
			// Windows: taskkill /T kills the whole process tree
			exec.Command("taskkill", "/PID", fmt.Sprintf("%d", pid), "/T", "/F").Run()
		} else if runtime.GOOS == "linux" {
			// Linux: Chromium-based browsers (360 etc.) fork many child processes
			// that survive parent kill. Must use SIGKILL (-9) and multiple methods.
			fmt.Printf("[INFO] Browser exe: %s (PID: %d)\n", browserExe, pid)

			// 1. Kill the main process with SIGKILL
			browserCmd.Process.Kill()

			// 2. pkill -9 by exe name: kills ALL processes matching the browser exe
			//    This catches forked child processes (renderer, GPU, etc.)
			fmt.Printf("[INFO] pkill -9 -f %s\n", browserExe)
			exec.Command("pkill", "-9", "-f", browserExe).Run()

			// 3. killall -9 as backup (matches by process name, not cmdline)
			exec.Command("killall", "-9", browserExe).Run()

			// 4. pkill -9 by user-data-dir (matches our launched instance)
			if browserProfileDir != "" {
				exec.Command("pkill", "-9", "-f", browserProfileDir).Run()
			}

			// 5. Kill the whole process group
			exec.Command("kill", "-9", "-"+fmt.Sprintf("%d", pid)).Run()

			// 6. 等待进程退出后删除 SingletonLock (奇安信用默认 profile, 需要清除锁)
			time.Sleep(1000 * time.Millisecond)
			if strings.Contains(browserExe, "qaxbrowser") {
				homeDir, _ := os.UserHomeDir()
				for _, profileName := range []string{"qaxbrowser-safe", "qaxbrowser-safe-stable", "qaxbrowser"} {
					singletonLock := filepath.Join(homeDir, ".config", profileName, "SingletonLock")
					os.Remove(singletonLock)
				}
			}
		} else {
			// Mac: kill + pkill by profile dir
			browserCmd.Process.Kill()
			if browserProfileDir != "" {
				profileName := filepath.Base(browserProfileDir)
				exec.Command("pkill", "-9", "-f", profileName).Run()
			}
			exec.Command("pkill", "-9", "-f", browserExe).Run()
		}
		fmt.Println("[INFO] Browser closed.")
	}

	// 清理临时 profile 目录
	if browserProfileDir != "" {
		_ = os.RemoveAll(browserProfileDir)
	}
}

// getExecutableDir returns the directory of the executable (with recover)
func getExecutableDir() string {
	defer func() {
		if r := recover(); r != nil {
			fmt.Printf("      [WARN] os.Executable() panic: %v\n", r)
		}
	}()
	if p, err := os.Executable(); err == nil {
		return filepath.Dir(p)
	}
	d, _ := os.Getwd()
	return d
}

// launchBrowserSafe wraps launchBrowser with panic recovery
func launchBrowserSafe() {
	defer func() {
		if r := recover(); r != nil {
			fmt.Printf("[WARN] Browser launch failed (recovered): %v\n", r)
			fmt.Printf("[INFO] Please manually open: %s\n", targetURL)
			fmt.Println("       with proxy 127.0.0.1:8899")
		}
	}()
	launchBrowser()
}

// readConfig reads the target URL from config.txt
func readConfig() string {
	for _, dir := range []string{getExecutableDir(), "."} {
		path := filepath.Join(dir, configName)
		data, err := os.ReadFile(path)
		if err != nil {
			continue
		}
		scanner := bufio.NewScanner(strings.NewReader(string(data)))
		for scanner.Scan() {
			line := strings.TrimSpace(scanner.Text())
			if line == "" || strings.HasPrefix(line, "#") {
				continue
			}
			return line
		}
	}
	return defaultPageURL
}

// handleProxyRequest handles all proxy requests: HTTP forwarding and HTTPS tunneling
func handleProxyRequest(w http.ResponseWriter, r *http.Request) {
	defer func() {
		if rec := recover(); rec != nil {
			log.Printf("[WARN] Request handler panic: %v\n", rec)
		}
	}()

	// [DEBUG] Log every incoming request to diagnose proxy traffic.
	fmt.Printf("[DEBUG] %s %s %s\n", r.Method, r.Host, r.URL.String())
	// 标记代理已收到流量 (用于无头模式降级判断)
	proxyTrafficReceived = true

	if r.Method == http.MethodConnect {
		handleTunneling(w, r)
		return
	}

	requestURL := r.URL.String()
	if strings.Contains(requestURL, targetAPIPath) {
		// Find Authorization header ignoring case, but preserve the original
		// key casing (HTTP header keys are case-insensitive, but callers may
		// expect a specific casing in the captured JSON output).
		found := false
		for key, values := range r.Header {
			if strings.EqualFold(key, "Authorization") && len(values) > 0 {
				captureAuth(requestURL, key, values[0])
				found = true
				break
			}
		}
		// [DEBUG] Matched target API path but Authorization header is missing.
		if !found {
			fmt.Printf("[DEBUG] Matched target API path but no Authorization header found.\n")
			fmt.Printf("[DEBUG] Request headers:\n")
			for k := range r.Header {
				fmt.Printf("[DEBUG]   %s\n", k)
			}
		}
	}

	resp, err := http.DefaultTransport.RoundTrip(r)
	if err != nil {
		log.Printf("Forward error for %s: %v\n", requestURL, err)
		http.Error(w, err.Error(), http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	// [DEBUG] Log forwarding result for requests matching the target API path.
	if strings.Contains(requestURL, targetAPIPath) {
		fmt.Printf("[DEBUG] Forwarded %s -> status %d\n", requestURL, resp.StatusCode)
	}

	for key, values := range resp.Header {
		for _, value := range values {
			w.Header().Add(key, value)
		}
	}
	w.WriteHeader(resp.StatusCode)
	io.Copy(w, resp.Body)
}

func handleTunneling(w http.ResponseWriter, r *http.Request) {
	// [DEBUG] Log CONNECT tunnel establishment (success/failure).
	destConn, err := net.DialTimeout("tcp", r.Host, 10*time.Second)
	if err != nil {
		fmt.Printf("[DEBUG] CONNECT %s FAILED: %v\n", r.Host, err)
		http.Error(w, err.Error(), http.StatusServiceUnavailable)
		return
	}
	fmt.Printf("[DEBUG] CONNECT %s -> tunnel established\n", r.Host)

	w.WriteHeader(http.StatusOK)
	hijacker, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "Hijacking not supported", http.StatusInternalServerError)
		return
	}

	clientConn, _, err := hijacker.Hijack()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	go transfer(destConn, clientConn)
	go transfer(clientConn, destConn)
}

func transfer(dest io.WriteCloser, src io.ReadCloser) {
	defer dest.Close()
	defer src.Close()
	io.Copy(dest, src)
}

func captureAuth(url, headerKey, auth string) {
	captureCount++
	timestamp := time.Now().Format("2006-01-02 15:04:05")

	fmt.Println()
	fmt.Println("==================================================")
	fmt.Printf("[CAPTURED #%d] %s\n", captureCount, timestamp)
	fmt.Printf("  URL: %s\n", url)
	fmt.Printf("  Header key: %s\n", headerKey)
	fmt.Println("--------------------------------------------------")
	fmt.Println("Authorization value:")
	fmt.Println("--------------------------------------------------")
	fmt.Println(auth)
	fmt.Println("--------------------------------------------------")

	// Write only the authorization header to result.txt as JSON (overwrite on each capture).
	// 每次都覆盖, 保证 result.txt 里始终是最新一次的 Authorization。
	data := map[string]string{
		headerKey: auth,
	}
	jsonBytes, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		fmt.Printf("[WARN] Failed to marshal JSON: %v\n", err)
	} else {
		resultPath := filepath.Join(exeDir, resultName)
		f, err := os.Create(resultPath)
		if err != nil {
			fmt.Printf("[WARN] Failed to create %s: %v\n", resultPath, err)
		} else {
			if _, werr := f.Write(jsonBytes); werr != nil {
				fmt.Printf("[WARN] Failed to write %s: %v\n", resultPath, werr)
			} else {
				fmt.Printf("[INFO] Captured data (capture #%d) written to %s\n", captureCount, resultPath)
			}
			f.Close()
		}
	}

	// 刷新抓取时间戳, 重置静默计时。
	// 不直接触发结束, 由 silenceWatcher 在静默超时后触发。
	lastCaptureTime = time.Now()
	fmt.Printf("[INFO] Waiting %v for no new queryUserInfo request before finishing...\n", silenceTimeout)
}

// silenceWatcher 监控 queryUserInfo 请求的静默状态。
// 每次收到 queryUserInfo 都会刷新 lastCaptureTime;
// 若超过 silenceTimeout 仍无新请求, 则认为页面已稳定, 触发结束流程。
// 这样 result.txt 里保存的是最后一次(通常也是最终有效)的 Authorization。
func silenceWatcher() {
	ticker := time.NewTicker(500 * time.Millisecond)
	defer ticker.Stop()
	for range ticker.C {
		if captureDone {
			return
		}
		if !lastCaptureTime.IsZero() && time.Since(lastCaptureTime) >= silenceTimeout {
			captureDone = true
			fmt.Printf("\n[INFO] No new queryUserInfo request for %v. Finalizing with capture #%d.\n",
				silenceTimeout, captureCount)
			select {
			case capturedCh <- struct{}{}:
			default:
			}
			return
		}
	}
}

// headlessFallbackWatcher 监控无头浏览器是否正常工作。
// 浏览器启动 15 秒后, 如果代理未收到任何请求, 说明无头模式可能卡住
// (Win7 上 GPU 初始化静默挂起等兼容性问题), 自动降级为有头模式重试。
func headlessFallbackWatcher() {
	// 等待浏览器启动
	time.Sleep(2 * time.Second)

	ticker := time.NewTicker(1 * time.Second)
	defer ticker.Stop()
	for range ticker.C {
		if captureDone || proxyTrafficReceived || headlessFallbackDone {
			return
		}
		if !browserLaunchTime.IsZero() && time.Since(browserLaunchTime) >= 15*time.Second {
			headlessFallbackDone = true
			fmt.Println()
			fmt.Println("==================================================")
			fmt.Println("[WARN] Headless browser produced no traffic for 15s.")
			fmt.Println("[WARN] Likely headless compatibility issue (common on Win7).")
			fmt.Println("[INFO] Falling back to visible browser mode...")
			fmt.Println("==================================================")
			// 关闭卡住的无头浏览器
			cleanupBrowser()
			// 清除无头参数, 降级为有头模式
			headlessArgs = nil
			// 重新启动浏览器
			launchBrowserSafe()
			return
		}
	}
}

// launchBrowser finds and launches a browser with proxy settings
func launchBrowser() {
	proxyFlag := fmt.Sprintf("--proxy-server=http://127.0.0.1:%d", proxyPort)
	// headlessArgs 为包级变量: 非空时使用无头模式, 降级后为 nil 使用有头模式
	// 用 /tmp 下的临时目录作为 profile，避免和已有浏览器 profile 版本冲突
	// (奇安信/360等 Chromium 内核浏览器升级后，旧 profile 会报"来自更高版本"错误)
	browserProfileDir = filepath.Join(os.TempDir(), fmt.Sprintf("capture-proxy-profile-%d", time.Now().UnixNano()))
	_ = os.MkdirAll(browserProfileDir, 0700)

	var cmd *exec.Cmd

	if runtime.GOOS == "darwin" {
		// Mac: Chromium browsers first
		browsers := []struct {
			path string
			name string
		}{
			{"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", "Google Chrome"},
			{"/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge", "Microsoft Edge"},
			{"/Applications/Brave Browser.app/Contents/MacOS/Brave Browser", "Brave"},
			{"/Applications/Chromium.app/Contents/MacOS/Chromium", "Chromium"},
		}
		for _, b := range browsers {
				if fileExists(b.path) {
					fmt.Printf("[INFO] Launching %s (headless) with proxy 127.0.0.1:%d...\n", b.name, proxyPort)
					cmd = exec.Command(b.path, browserArgs(proxyFlag, "--user-data-dir="+browserProfileDir, "--no-first-run", "--no-default-browser-check", targetURL)...)
					break
				}
			}
		// Fallback: Safari (system-wide proxy)
		if cmd == nil {
			if fileExists("/Applications/Safari.app") {
				fmt.Println("[INFO] No Chromium browser found. Setting up system proxy for Safari...")
				netService = detectNetworkService()
				if netService != "" {
					safariMode = true
					exec.Command("networksetup", "-setwebproxy", netService, "127.0.0.1", fmt.Sprintf("%d", proxyPort)).Run()
					exec.Command("networksetup", "-setsecurewebproxy", netService, "127.0.0.1", fmt.Sprintf("%d", proxyPort)).Run()
					fmt.Printf("[INFO] System proxy set to 127.0.0.1:%d (service: %s)\n", proxyPort, netService)
					cmd = exec.Command("open", "-a", "Safari", targetURL)
				}
			}
		}
	} else if runtime.GOOS == "linux" {
		// Linux (incl. Kylin/UOS): find Chromium-based browsers via PATH
		// 360企业安全浏览器 on Linux is Chromium-based, supports --proxy-server
		linuxBrowsers := []struct {
			cmd  string
			name string
		}{
			// 奇安信可信浏览器 (Chromium-based, 默认浏览器 on Kylin/UOS 金融版)
			{"qaxbrowser-safe-stable", "奇安信可信浏览器"},
			{"qaxbrowser-safe", "奇安信可信浏览器"},
			// 360企业安全浏览器 - 实际命令名带 -cn 后缀
			{"browser360ent-cn-stable", "360企业安全浏览器"},
			{"browser360ent-cn", "360企业安全浏览器"},
			{"360entbrowser", "360企业安全浏览器"},
			{"360esb", "360企业安全浏览器"},
			{"360safe-browser", "360安全浏览器"},
			{"360se", "360安全浏览器"},
			{"browser360-cn", "360浏览器"},
			{"google-chrome", "Google Chrome"},
			{"google-chrome-stable", "Google Chrome"},
			{"chromium", "Chromium"},
			{"chromium-browser", "Chromium"},
			{"microsoft-edge", "Microsoft Edge"},
			{"microsoft-edge-stable", "Microsoft Edge"},
		}
		for _, b := range linuxBrowsers {
				if path, err := exec.LookPath(b.cmd); err == nil {
						fmt.Printf("[INFO] Launching %s (%s) with proxy 127.0.0.1:%d...\n", b.name, path, proxyPort)
						// 奇安信可信浏览器: 必须使用默认 profile (含管控中心认证)
						// 不传 --user-data-dir, 先杀掉已存在的实例 (避免 profile 锁冲突)
						if strings.Contains(b.cmd, "qaxbrowser") {
							fmt.Println("[INFO] Closing existing 奇安信可信浏览器 instances...")
							// 多轮 pkill: 宽泛匹配所有 qaxbrowser 相关进程 (主进程 + renderer/GPU 子进程)
							// 第 1 轮: 杀主进程和大部分子进程
							exec.Command("pkill", "-9", "-f", "qaxbrowser").Run()
							exec.Command("killall", "-9", "qaxbrowser-safe-stable").Run()
							exec.Command("killall", "-9", "qaxbrowser-safe").Run()
							time.Sleep(800 * time.Millisecond)
							// 第 2 轮: 杀残留子进程 (renderer 等可能需要更久才退出)
							exec.Command("pkill", "-9", "-f", "qaxbrowser").Run()
							time.Sleep(800 * time.Millisecond)

							// 删除 SingletonLock, 防止新进程把 URL 转发给已死实例
							homeDir, _ := os.UserHomeDir()
							for _, profileName := range []string{"qaxbrowser-safe", "qaxbrowser-safe-stable", "qaxbrowser"} {
								singletonLock := filepath.Join(homeDir, ".config", profileName, "SingletonLock")
								if _, err := os.Stat(singletonLock); err == nil {
									fmt.Printf("[INFO] Removing stale SingletonLock: %s\n", singletonLock)
									os.Remove(singletonLock)
								}
							}
							// 奇安信浏览器 --proxy-server 会把内部代理改成"使用系统代理设置"并保存
							// 后续 --proxy-server 失效, 改用 gsettings 系统代理让浏览器走代理 (双保险)
							// 但全局代理会导致页面所有资源都走代理, 某些请求转发失败则页面打不开
							// 解决: 改用 PAC 文件, 只让 /api/user/queryUserInfo 走代理, 其余直连
							fmt.Println("[INFO] Setting PAC proxy for 奇安信可信浏览器 (API-only proxy)...")
							if setLinuxPacProxy() {
								linuxProxyMode = true // 确保退出时恢复 gsettings
							}
							// 同时传 --proxy-pac-url (首次运行浏览器未改设置时仍然有效)
							pacUrlFlag := "--proxy-pac-url=file://" + pacFilePath
							cmd = exec.Command(path, browserArgs(pacUrlFlag, "--no-first-run", "--no-default-browser-check", targetURL)...)
							browserProfileDir = "" // 标记不使用临时 profile
						} else {
							cmd = exec.Command(path, browserArgs(proxyFlag, "--user-data-dir="+browserProfileDir, "--no-first-run", "--no-default-browser-check", targetURL)...)
						}
						break
					}
			}
		if cmd == nil {
			// Try common full paths for 360 browsers and others
			linuxPaths := []struct {
				path string
				name string
			}{
				// 360企业安全浏览器 / 360安全浏览器 on Linux
				{"/opt/360/360se/360se", "360安全浏览器"},
				{"/opt/360/360safe/360safe-browser", "360安全浏览器"},
				{"/opt/360/360entbrowser/360entbrowser", "360企业安全浏览器"},
				{"/opt/360/360esb/360esb", "360企业安全浏览器"},
				{"/opt/360safe-browser/360safe-browser", "360安全浏览器"},
				{"/opt/browser360-cn/browser360-cn", "360浏览器"},
				{"/usr/bin/360safe-browser", "360安全浏览器"},
				{"/usr/bin/360se", "360安全浏览器"},
				{"/usr/bin/360entbrowser", "360企业安全浏览器"},
				{"/usr/bin/browser360-cn", "360浏览器"},
				// Google Chrome
				{"/usr/bin/google-chrome", "Google Chrome"},
				{"/usr/bin/google-chrome-stable", "Google Chrome"},
				{"/opt/google/chrome/chrome", "Google Chrome"},
				// Chromium
				{"/usr/bin/chromium", "Chromium"},
				{"/usr/bin/chromium-browser", "Chromium"},
				// Microsoft Edge
				{"/usr/bin/microsoft-edge", "Microsoft Edge"},
				{"/opt/microsoft/msedge/msedge", "Microsoft Edge"},
			}
			for _, b := range linuxPaths {
				if fileExists(b.path) {
					fmt.Printf("[INFO] Launching %s (%s, headless) with proxy 127.0.0.1:%d...\n", b.name, b.path, proxyPort)
					cmd = exec.Command(b.path, browserArgs(proxyFlag, "--user-data-dir="+browserProfileDir, "--no-first-run", "--no-default-browser-check", targetURL)...)
					break
				}
			}
		}

		// Ultimate fallback on Linux: use xdg-open with system proxy via gsettings
		if cmd == nil {
			fmt.Println("[INFO] No browser found by path or PATH.")
			fmt.Println("[INFO] Trying xdg-open with system proxy...")
			if setLinuxSystemProxy() {
				linuxProxyMode = true
				cmd = exec.Command("xdg-open", targetURL)
			}
		}
	} else if runtime.GOOS == "windows" {
		programFiles := os.Getenv("ProgramFiles")
		programFilesX86 := os.Getenv("ProgramFiles(x86)")
		localAppData := os.Getenv("LOCALAPPDATA")
		appData := os.Getenv("APPDATA")

		// Step 1: Chromium-based browsers that support --proxy-server directly.
		// Includes 360极速浏览器, 360企业安全浏览器 (Chromium-based).
		chromiumBrowsers := []struct {
			path string
			name string
		}{
			// Google Chrome
			{filepath.Join(programFiles, "Google", "Chrome", "Application", "chrome.exe"), "Google Chrome"},
			{filepath.Join(programFilesX86, "Google", "Chrome", "Application", "chrome.exe"), "Google Chrome (x86)"},
			// Microsoft Edge
			{filepath.Join(programFiles, "Microsoft", "Edge", "Application", "msedge.exe"), "Microsoft Edge"},
			{filepath.Join(programFilesX86, "Microsoft", "Edge", "Application", "msedge.exe"), "Microsoft Edge (x86)"},
			// 360极速浏览器 (Chromium-based)
			{filepath.Join(programFilesX86, "360Chrome", "Chrome", "Application", "360chrome.exe"), "360极速浏览器"},
			{filepath.Join(programFiles, "360Chrome", "Chrome", "Application", "360chrome.exe"), "360极速浏览器"},
			{filepath.Join(programFilesX86, "360", "360Chrome", "Chrome", "Application", "360chrome.exe"), "360极速浏览器"},
			{filepath.Join(localAppData, "360Chrome", "Chrome", "Application", "360chrome.exe"), "360极速浏览器"},
			// 360企业安全浏览器 (Chromium-based) - multiple possible install paths
			{filepath.Join(programFiles, "360", "360Edge", "Application", "360entbrowser.exe"), "360企业安全浏览器"},
			{filepath.Join(programFilesX86, "360", "360Edge", "Application", "360entbrowser.exe"), "360企业安全浏览器"},
			{filepath.Join(programFiles, "360", "360EntBrowser", "Application", "360entbrowser.exe"), "360企业安全浏览器"},
			{filepath.Join(programFilesX86, "360", "360EntBrowser", "Application", "360entbrowser.exe"), "360企业安全浏览器"},
			{filepath.Join(programFiles, "360", "360ESB", "Application", "360entbrowser.exe"), "360企业安全浏览器"},
			{filepath.Join(programFilesX86, "360", "360ESB", "Application", "360entbrowser.exe"), "360企业安全浏览器"},
			{filepath.Join(programFilesX86, "360Chrome", "Chrome", "Application", "360entbrowser.exe"), "360企业安全浏览器"},
			{filepath.Join(programFiles, "360Chrome", "Chrome", "Application", "360entbrowser.exe"), "360企业安全浏览器"},
			{filepath.Join(localAppData, "360Chrome", "Chrome", "Application", "360entbrowser.exe"), "360企业安全浏览器"},
		}
		for _, b := range chromiumBrowsers {
			if fileExists(b.path) {
				fmt.Printf("[INFO] Launching %s (headless) with proxy 127.0.0.1:%d...\n", b.name, proxyPort)
				cmd = exec.Command(b.path, browserArgs(proxyFlag, "--user-data-dir="+browserProfileDir, "--no-first-run", "--no-default-browser-check", targetURL)...)
				break
			}
		}

		// Step 2: Try finding browser path via Windows registry
		if cmd == nil {
			fmt.Println("[INFO] Searching for browsers via Windows registry...")
			regPaths := []struct {
				key  string
				name string
			}{
				{`SOFTWARE\Clients\StartMenuInternet\360Chrome\shell\open\command`, "360浏览器 (registry)"},
				{`SOFTWARE\Clients\StartMenuInternet\360ESB\shell\open\command`, "360企业安全浏览器 (registry)"},
				{`SOFTWARE\Clients\StartMenuInternet\360EntBrowser\shell\open\command`, "360企业安全浏览器 (registry)"},
				{`SOFTWARE\Clients\StartMenuInternet\Google Chrome\shell\open\command`, "Google Chrome (registry)"},
				{`SOFTWARE\Clients\StartMenuInternet\Microsoft Edge\shell\open\command`, "Microsoft Edge (registry)"},
			}
			for _, r := range regPaths {
				if p := findBrowserFromRegistry(r.key, "HKEY_LOCAL_MACHINE"); p != "" {
					p = strings.Trim(p, "\"")
					// Registry value is usually: "C:\path\to\browser.exe" %1
					// Extract the exe path
					if strings.HasPrefix(p, "\"") {
						p = strings.TrimPrefix(p, "\"")
						if idx := strings.Index(p, "\""); idx > 0 {
							p = p[:idx]
						}
					}
					if fileExists(p) {
						fmt.Printf("[INFO] Found %s via registry: %s\n", r.name, p)
						fmt.Printf("[INFO] Launching (headless) with proxy 127.0.0.1:%d...\n", proxyPort)
						cmd = exec.Command(p, browserArgs(proxyFlag, "--user-data-dir="+browserProfileDir, "--no-first-run", "--no-default-browser-check", targetURL)...)
						break
					}
				}
				// Also check HKEY_CURRENT_USER
				if p := findBrowserFromRegistry(r.key, "HKEY_CURRENT_USER"); p != "" {
					p = strings.Trim(p, "\"")
					if strings.HasPrefix(p, "\"") {
						p = strings.TrimPrefix(p, "\"")
						if idx := strings.Index(p, "\""); idx > 0 {
							p = p[:idx]
						}
					}
					if fileExists(p) {
						fmt.Printf("[INFO] Found %s via registry (HKCU): %s\n", r.name, p)
						fmt.Printf("[INFO] Launching (headless) with proxy 127.0.0.1:%d...\n", proxyPort)
						cmd = exec.Command(p, browserArgs(proxyFlag, "--user-data-dir="+browserProfileDir, "--no-first-run", "--no-default-browser-check", targetURL)...)
						break
					}
				}
			}
		}

		// Step 3: IE-based browsers (360安全浏览器 personal) - need system-wide proxy
		if cmd == nil {
			ieBrowsers := []struct {
				path string
				name string
			}{
				{filepath.Join(programFilesX86, "360se6", "Application", "360se.exe"), "360安全浏览器"},
				{filepath.Join(programFiles, "360se6", "Application", "360se.exe"), "360安全浏览器"},
				{filepath.Join(programFilesX86, "360", "360se", "Application", "360se.exe"), "360安全浏览器"},
				{filepath.Join(programFilesX86, "360Chrome", "360se6", "Application", "360se.exe"), "360安全浏览器"},
				{filepath.Join(appData, "360se6", "Application", "360se.exe"), "360安全浏览器"},
			}
			for _, b := range ieBrowsers {
				if fileExists(b.path) {
					fmt.Printf("[INFO] Launching %s (IE-based, setting system proxy)...\n", b.name)
					if setWindowsSystemProxy() {
						windowsProxyMode = true
						cmd = exec.Command(b.path, targetURL)
						break
					}
				}
			}
		}

		// Step 4: Ultimate fallback - set system proxy + open with default browser
		if cmd == nil {
			fmt.Println("[INFO] No browser found by path or registry.")
			fmt.Println("[INFO] Setting system proxy and opening with default browser...")
			if setWindowsSystemProxy() {
				windowsProxyMode = true
				cmd = exec.Command("cmd", "/c", "start", "", targetURL)
			}
		}
	}

	if cmd == nil {
		fmt.Println("[WARN] No supported Chromium browser found.")
		fmt.Printf("       Please manually open: %s\n", targetURL)
		fmt.Println("       with proxy 127.0.0.1:8899")
		return
	}

	cmd.Stdin = nil
	cmd.Stdout = nil
	cmd.Stderr = os.Stderr  // 捕获浏览器日志 (含 --enable-logging=stderr 输出), 便于排查卡住原因
	if err := cmd.Start(); err != nil {
		fmt.Printf("[WARN] Failed to launch browser: %v\n", err)
		fmt.Printf("[INFO] Please manually open: %s\n", targetURL)
		fmt.Println("       with proxy 127.0.0.1:8899")
		return
	}
	browserCmd = cmd
	browserLaunchTime = time.Now()
	fmt.Printf("[INFO] Browser launched (PID: %d)\n", cmd.Process.Pid)
}

// detectNetworkService finds the active network service name on macOS
func detectNetworkService() string {
	out, err := exec.Command("networksetup", "-listnetworkserviceorder").Output()
	if err != nil {
		return "Wi-Fi"
	}
	scanner := bufio.NewScanner(strings.NewReader(string(out)))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if strings.HasPrefix(line, "(") && strings.Contains(line, ")") {
			idx := strings.LastIndex(line, "(")
			if idx >= 0 {
				name := strings.TrimSpace(line[idx+1:])
				name = strings.TrimSuffix(name, ")")
				if name != "" && name != "An asterisk" {
					return name
				}
			}
		}
	}
	return "Wi-Fi"
}

func fileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}

// setLinuxSystemProxy sets the GNOME/system HTTP proxy for Linux desktop environments.
// Uses gsettings (GNOME) which also works on Kylin/UOS.
func setLinuxSystemProxy() bool {
	proxyAddr := fmt.Sprintf("127.0.0.1:%d", proxyPort)
	// Try gsettings (GNOME-based: Ubuntu, Kylin, UOS)
	commands := [][]string{
		{"gsettings", "set", "org.gnome.system.proxy", "mode", "'manual'"},
		{"gsettings", "set", "org.gnome.system.proxy.http", "host", "'127.0.0.1'"},
		{"gsettings", "set", "org.gnome.system.proxy.http", "port", fmt.Sprintf("%d", proxyPort)},
		{"gsettings", "set", "org.gnome.system.proxy.https", "host", "'127.0.0.1'"},
		{"gsettings", "set", "org.gnome.system.proxy.https", "port", fmt.Sprintf("%d", proxyPort)},
	}
	success := false
	for _, c := range commands {
		if err := exec.Command(c[0], c[1:]...).Run(); err == nil {
			success = true
		}
	}
	if success {
		fmt.Printf("[INFO] Linux system proxy set to %s (via gsettings)\n", proxyAddr)
		return true
	}
	fmt.Println("[WARN] Could not set Linux system proxy via gsettings.")
	return false
}

// setLinuxPacProxy 生成 PAC 文件并通过 gsettings 设置为系统代理。
// PAC 文件只将 /api/user/queryUserInfo 请求路由到代理, 其余流量直连。
// 这样奇安信浏览器能正常加载页面, 只有目标 API 请求被代理捕获。
func setLinuxPacProxy() bool {
	// 从 targetURL 提取 host (用于 HTTPS 匹配, HTTPS 时 PAC 无法看到路径)
	targetHost := strings.TrimPrefix(strings.TrimPrefix(targetURL, "http://"), "https://")
	if idx := strings.Index(targetHost, "/"); idx > 0 {
		targetHost = targetHost[:idx]
	}
	// 去掉端口号
	if idx := strings.Index(targetHost, ":"); idx > 0 {
		targetHost = targetHost[:idx]
	}

	// 生成 PAC 文件
	pacFilePath = filepath.Join(os.TempDir(), fmt.Sprintf("capture-proxy-%d.pac", time.Now().UnixNano()))
	pacContent := fmt.Sprintf(`function FindProxyForURL(url, host) {
    // HTTP: 按路径匹配, 只让目标 API 走代理
    if (shExpMatch(url, "*/api/user/queryUserInfo*")) {
        return "PROXY 127.0.0.1:%d";
    }
    // HTTPS: 看不到路径, 按域名匹配目标站点
    if (url.substring(0, 5) == "https" && host == "%s") {
        return "PROXY 127.0.0.1:%d";
    }
    return "DIRECT";
}
`, proxyPort, targetHost, proxyPort)

	if err := os.WriteFile(pacFilePath, []byte(pacContent), 0644); err != nil {
		fmt.Printf("[WARN] Failed to write PAC file: %v\n", err)
		return false
	}
	fmt.Printf("[INFO] PAC file written: %s\n", pacFilePath)
	fmt.Printf("[INFO] PAC proxy: only /api/user/queryUserInfo -> 127.0.0.1:%d, rest direct\n", proxyPort)

	// 通过 gsettings 设置 PAC 代理 (mode=auto)
	pacUrl := "file://" + pacFilePath
	commands := [][]string{
		{"gsettings", "set", "org.gnome.system.proxy", "mode", "'auto'"},
		{"gsettings", "set", "org.gnome.system.proxy", "autoconfig-url", "'" + pacUrl + "'"},
	}
	success := false
	for _, c := range commands {
		if err := exec.Command(c[0], c[1:]...).Run(); err == nil {
			success = true
		}
	}
	if success {
		fmt.Printf("[INFO] gsettings PAC proxy set: %s\n", pacUrl)
		return true
	}
	fmt.Println("[WARN] Could not set gsettings PAC proxy.")
	return false
}

// restoreLinuxSystemProxy restores the GNOME/system HTTP proxy settings.
func restoreLinuxSystemProxy() {
	exec.Command("gsettings", "set", "org.gnome.system.proxy", "mode", "'none'").Run()
	// 清理 PAC 文件
	if pacFilePath != "" {
		os.Remove(pacFilePath)
		pacFilePath = ""
	}
}

// findBrowserFromRegistry queries a registry key for a browser path.
// rootKey is "HKEY_LOCAL_MACHINE" or "HKEY_CURRENT_USER".
func findBrowserFromRegistry(keyPath, rootKey string) string {
	// Use reg query command: reg query "HKLM\SOFTWARE\..." /ve
	// /ve queries the default value
	regCmd := "reg"
	args := []string{"query", rootKey + "\\" + keyPath, "/ve"}
	out, err := exec.Command(regCmd, args...).Output()
	if err != nil {
		return ""
	}
	// Output looks like:
	// HKEY_LOCAL_MACHINE\SOFTWARE\...\shell\open\command
	//     (Default)    REG_SZ    "C:\pathrowser.exe" %1
	scanner := bufio.NewScanner(strings.NewReader(string(out)))
	for scanner.Scan() {
		line := scanner.Text()
		if strings.Contains(line, "REG_SZ") {
			parts := strings.SplitN(line, "REG_SZ", 2)
			if len(parts) == 2 {
				return strings.TrimSpace(parts[1])
			}
		}
	}
	return ""
}

// setWindowsSystemProxy sets the Windows system-wide HTTP proxy via registry.
// Used for IE-based browsers (360 Safe Browser) that don't support --proxy-server.
func setWindowsSystemProxy() bool {
	proxyAddr := fmt.Sprintf("127.0.0.1:%d", proxyPort)
	// Set proxy in registry: HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings
	regScript := fmt.Sprintf(
		`reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyEnable /t REG_DWORD /d 1 /f >nul 2>&1 && reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyServer /t REG_SZ /d "%s" /f >nul 2>&1`,
		proxyAddr)
	cmd := exec.Command("cmd", "/c", regScript)
	err := cmd.Run()
	if err != nil {
		fmt.Printf("[WARN] Failed to set Windows system proxy: %v\n", err)
		return false
	}
	fmt.Printf("[INFO] Windows system proxy set to %s\n", proxyAddr)
	// Notify IE-based browsers to refresh proxy settings
	refreshCmd := exec.Command("rundll32", "wininet.dll", "InternetSetOptionW", "0", "0")
	refreshCmd.Run()
	return true
}

// restoreWindowsSystemProxy removes the Windows system-wide HTTP proxy setting.
func restoreWindowsSystemProxy() {
	// Disable proxy in registry
	exec.Command("cmd", "/c",
		`reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyEnable /t REG_DWORD /d 0 /f >nul 2>&1`).Run()
	// Notify browsers to refresh
	exec.Command("rundll32", "wininet.dll", "InternetSetOptionW", "0", "0").Run()
}
