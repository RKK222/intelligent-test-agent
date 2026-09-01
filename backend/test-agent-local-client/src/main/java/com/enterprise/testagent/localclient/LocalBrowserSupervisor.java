package com.enterprise.testagent.localclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 监管 TestAgent 专用 360 浏览器进程；只暴露 loopback CDP，不接管用户日常 profile。 */
final class LocalBrowserSupervisor implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalBrowserSupervisor.class);
    private static final Duration START_TIMEOUT = Duration.ofSeconds(20);
    private static final int MIN_CHROMIUM_MAJOR = 108;
    private static final int MAX_CHROMIUM_MAJOR = 149;
    private final LocalBrowserSettings settings;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private ManagedProcess managed;

    LocalBrowserSupervisor(LocalBrowserSettings settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    synchronized BrowserStatus start() {
        BrowserStatus current = status();
        if (current.running() && current.compatible()) {
            return current;
        }
        if (managed != null) {
            stop();
        }
        Path executable = settings.resolveExecutable();
        Path profile = settings.profileDirectory();
        try {
            profile = settings.prepareProfileDirectory();
            Files.deleteIfExists(profile.resolve("DevToolsActivePort"));
            List<String> command = new ArrayList<>();
            command.add(executable.toString());
            command.add("--remote-debugging-address=127.0.0.1");
            command.add("--remote-debugging-port=0");
            command.add("--user-data-dir=" + profile);
            command.add("--no-first-run");
            command.add("--no-default-browser-check");
            command.add("about:blank");
            Process process = new ProcessBuilder(command)
                    // 浏览器输出可能含页面 URL 或站点诊断信息，不进入客户端可导出的日志目录。
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            Instant startedAt = process.info().startInstant()
                    .orElseThrow(() -> new IllegalStateException("浏览器进程缺少启动时间"));
            ManagedProcess pending = new ManagedProcess(
                    process.toHandle(), process.pid(), startedAt, profile, null);
            managed = pending;
            CdpEndpoint endpoint = awaitEndpoint(pending);
            managed = pending.withEndpoint(endpoint);
            BrowserStatus started = status();
            if (!started.compatible()) {
                stop();
                throw new IllegalStateException("360 浏览器内核或 CDP 能力不在已验证范围");
            }
            LOGGER.info("local_browser_started processId={} chromiumMajor={} protocolVersion={}",
                    process.pid(), started.chromiumMajor(), started.protocolVersion());
            return started;
        } catch (IOException exception) {
            cleanupFailedStart();
            throw new IllegalStateException("360 浏览器启动失败", exception);
        } catch (RuntimeException exception) {
            cleanupFailedStart();
            throw exception;
        }
    }

    synchronized BrowserStatus status() {
        ManagedProcess current = managed;
        if (current == null) {
            return BrowserStatus.stopped();
        }
        if (!identityMatches(current)) {
            managed = null;
            return BrowserStatus.stopped();
        }
        if (current.endpoint() == null) {
            return new BrowserStatus(true, false, null, null, null, "STARTING", null);
        }
        try {
            JsonNode version = fetchJson(current.endpoint().httpBase().resolve("/json/version"));
            String browser = version.path("Browser").asText("");
            String userAgent = version.path("User-Agent").asText("");
            String protocol = version.path("Protocol-Version").asText("");
            String webSocket = version.path("webSocketDebuggerUrl").asText("");
            int major = chromiumMajor(browser, userAgent);
            boolean compatible = "1.3".equals(protocol)
                    && major >= MIN_CHROMIUM_MAJOR
                    && major <= MAX_CHROMIUM_MAJOR
                    && webSocket.startsWith("ws://127.0.0.1:");
            return new BrowserStatus(true, compatible, browser, protocol, major,
                    compatible ? "READY" : "INCOMPATIBLE", current.endpoint().httpBase().toString());
        } catch (RuntimeException exception) {
            return new BrowserStatus(true, false, null, null, null, "UNHEALTHY", null);
        }
    }

    synchronized BrowserStatus stop() {
        ManagedProcess current = managed;
        if (current == null) {
            return BrowserStatus.stopped();
        }
        if (identityMatches(current)) {
            current.process().descendants().forEach(ProcessHandle::destroy);
            current.process().destroy();
            waitForExit(current.process(), Duration.ofSeconds(5));
            if (current.process().isAlive()) {
                current.process().descendants().forEach(ProcessHandle::destroyForcibly);
                current.process().destroyForcibly();
                waitForExit(current.process(), Duration.ofSeconds(5));
            }
        }
        managed = null;
        LOGGER.info("local_browser_stopped processId={}", current.pid());
        return BrowserStatus.stopped();
    }

    private CdpEndpoint awaitEndpoint(ManagedProcess process) {
        Path activePort = process.profile().resolve("DevToolsActivePort");
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.process().isAlive()) {
                throw new IllegalStateException("360 浏览器在 CDP 就绪前退出");
            }
            try {
                if (Files.isRegularFile(activePort)) {
                    List<String> lines = Files.readAllLines(activePort, StandardCharsets.US_ASCII);
                    int port = Integer.parseInt(lines.getFirst().trim());
                    if (port > 0 && port <= 65535) {
                        URI base = URI.create("http://127.0.0.1:" + port);
                        JsonNode version = fetchJson(base.resolve("/json/version"));
                        if (!version.path("webSocketDebuggerUrl").asText("").isBlank()) {
                            return new CdpEndpoint(base);
                        }
                    }
                }
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (IOException | NumberFormatException ignored) {
                // 文件正在原子形成时继续等待。
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("浏览器启动等待被中断", exception);
            }
        }
        throw new IllegalStateException("360 浏览器 CDP 启动超时");
    }

    private JsonNode fetchJson(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 1024 * 1024) {
                throw new IllegalStateException("浏览器 CDP 版本响应无效");
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("浏览器 CDP 不可访问", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("浏览器 CDP 请求被中断", exception);
        }
    }

    static int chromiumMajor(String browser, String userAgent) {
        for (String value : List.of(browser == null ? "" : browser, userAgent == null ? "" : userAgent)) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?i)(?:Chrome|Chromium)/([0-9]{2,3})\\.")
                    .matcher(value);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return -1;
    }

    private boolean identityMatches(ManagedProcess current) {
        return sameProcessIdentity(current.process(), current.pid(), current.startedAt());
    }

    /**
     * 浏览器启动器允许通过 exec 原地切换为真实内核进程；此时 PID 和权威启动时间不变，但 command 会从
     * browser360ent-cn 变为 browser360ent。进程身份因此只使用不可跨 PID 复用的 PID + 启动时间，CDP
     * 端点及版本仍由 status 单独校验，不能把不稳定的启动器路径当作身份字段。
     */
    static boolean sameProcessIdentity(ProcessHandle process, long expectedPid, Instant expectedStartedAt) {
        if (!process.isAlive() || process.pid() != expectedPid) {
            return false;
        }
        return process.info().startInstant().map(expectedStartedAt::equals).orElse(false);
    }

    private void cleanupFailedStart() {
        if (managed != null) {
            stop();
        }
    }

    private static void waitForExit(ProcessHandle process, Duration timeout) {
        try {
            process.onExit().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // 调用方会再次检查 isAlive 并按需强制终止。
        }
    }

    @Override
    public void close() {
        stop();
    }

    record BrowserStatus(
            boolean running,
            boolean compatible,
            String browserVersion,
            String protocolVersion,
            Integer chromiumMajor,
            String status,
            String cdpEndpoint) {
        static BrowserStatus stopped() {
            return new BrowserStatus(false, false, null, null, null, "STOPPED", null);
        }
    }

    private record CdpEndpoint(URI httpBase) {
    }

    private record ManagedProcess(
            ProcessHandle process,
            long pid,
            Instant startedAt,
            Path profile,
            CdpEndpoint endpoint) {
        ManagedProcess withEndpoint(CdpEndpoint value) {
            return new ManagedProcess(process, pid, startedAt, profile, value);
        }
    }
}
