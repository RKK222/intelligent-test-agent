package com.enterprise.testagent.localclient;

import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 本地 OpenCode 监管器；PID、实际启动时间和可执行文件全部匹配后才允许停止。 */
final class OpencodeProcessSupervisor {

    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HEALTH_POLL_INTERVAL = Duration.ofMillis(250);
    private final LocalClientConfiguration configuration;
    private final LocalClientStateStore stateStore;
    private final LocalModelRelay modelRelay;
    private final LocalObservabilityRelay observabilityRelay;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    OpencodeProcessSupervisor(
            LocalClientConfiguration configuration,
            LocalClientStateStore stateStore,
            LocalModelRelay modelRelay) {
        this(configuration, stateStore, modelRelay, null);
    }

    OpencodeProcessSupervisor(
            LocalClientConfiguration configuration,
            LocalClientStateStore stateStore,
            LocalModelRelay modelRelay,
            LocalObservabilityRelay observabilityRelay) {
        this.configuration = configuration;
        this.stateStore = stateStore;
        this.modelRelay = modelRelay;
        this.observabilityRelay = observabilityRelay;
    }

    synchronized LocalClientPayloads.LifecycleResult start(Integer preferredPort) {
        LocalClientPayloads.LifecycleResult current = status();
        if (current.success() && "RUNNING".equals(current.processStatus()) && current.opencodeHealthy()) {
            return current;
        }
        if ("FAILED".equals(current.processStatus()) && current.processId() != null) {
            return current;
        }
        if (current.processId() != null) {
            // 已登记进程仍存活但 health 不健康时必须先按 PID/启动时间/命令身份停止，禁止另起孤儿进程。
            LocalClientPayloads.LifecycleResult stopped = stop();
            if (!stopped.success()) {
                return stopped;
            }
        }
        Path executable = requireExecutable();
        List<Integer> ports = candidatePorts(preferredPort);
        RuntimeException lastFailure = null;
        for (int port : ports) {
            if (!portAvailable(port)) {
                continue;
            }
            try {
                return startOnPort(executable, port);
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        String message = lastFailure == null ? "没有可用的本地 OpenCode 端口" : "本地 OpenCode 启动失败";
        return result(false, "FAILED", null, null, null, false, executable.toString(), message);
    }

    synchronized LocalClientPayloads.LifecycleResult restart(Integer preferredPort) {
        LocalClientPayloads.LifecycleResult stopped = stop();
        if (!stopped.success()) {
            return stopped;
        }
        return start(preferredPort);
    }

    synchronized LocalClientPayloads.LifecycleResult stop() {
        LocalClientPersistentState.ProcessState recorded = stateStore.read().process();
        if (recorded == null) {
            return result(true, "STOPPED", null, null, null, false, requireExecutable().toString(), "已停止");
        }
        ProcessHandle handle = ProcessHandle.of(recorded.processId()).orElse(null);
        if (handle == null || !handle.isAlive()) {
            // 权威 PID 已退出时，端口上的任何新进程都不再属于本客户端：只清过期记录，绝不控制陌生进程。
            // 后续 start 会通过受控端口探测跳过占用端口，避免陈旧状态永久阻断自动恢复。
            clearProcess(recorded);
            return result(true, "STOPPED", null, null, recorded.port(), false, recorded.executable(), "原进程已退出");
        }
        IdentityCheck identity = checkIdentity(handle, recorded);
        if (!identity.matches()) {
            return result(false, "FAILED", recorded.processId(), recorded.startedAt(), recorded.port(),
                    healthy(recorded.port()), recorded.executable(), identity.message());
        }
        handle.destroy();
        waitForExit(handle, Duration.ofSeconds(5));
        if (handle.isAlive()) {
            handle.destroyForcibly();
            waitForExit(handle, Duration.ofSeconds(5));
        }
        if (handle.isAlive() || healthy(recorded.port())) {
            return result(false, "FAILED", recorded.processId(), recorded.startedAt(), recorded.port(),
                    healthy(recorded.port()), recorded.executable(), "本地 OpenCode 停止确认失败");
        }
        clearProcess(recorded);
        return result(true, "STOPPED", null, null, recorded.port(), false, recorded.executable(), "已停止");
    }

    synchronized LocalClientPayloads.LifecycleResult status() {
        LocalClientPersistentState.ProcessState recorded = stateStore.read().process();
        Path executable = requireExecutable();
        if (recorded == null) {
            return result(true, "STOPPED", null, null, null, false, executable.toString(), "未启动");
        }
        ProcessHandle handle = ProcessHandle.of(recorded.processId()).orElse(null);
        if (handle == null || !handle.isAlive()) {
            // 不能用端口 health 替代 PID/启动时间身份；即使端口被其它 OpenCode 占用，也只清理本客户端旧记录。
            clearProcess(recorded);
            return result(true, "STOPPED", null, null, recorded.port(), false, recorded.executable(), "进程已退出");
        }
        IdentityCheck identity = checkIdentity(handle, recorded);
        boolean health = healthy(recorded.port());
        if (!identity.matches()) {
            return result(false, "FAILED", recorded.processId(), recorded.startedAt(), recorded.port(), health,
                    recorded.executable(), identity.message());
        }
        return result(
                health,
                health ? "RUNNING" : "UNHEALTHY",
                recorded.processId(),
                recorded.startedAt(),
                recorded.port(),
                health,
                recorded.executable(),
                health ? "运行中" : "进程存在但 loopback health 不健康");
    }

    private LocalClientPayloads.LifecycleResult startOnPort(Path executable, int port) {
        Process process = null;
        LocalClientPersistentState.ProcessState recorded = null;
        try {
            Files.createDirectories(configuration.opencodeConfigDirectory());
            Files.createDirectories(configuration.opencodeDataDirectory());
            Path dataParent = configuration.opencodeDataDirectory().toAbsolutePath().normalize().getParent();
            Path logDirectory = (dataParent == null ? configuration.opencodeDataDirectory() : dataParent)
                    .resolve("logs");
            Files.createDirectories(logDirectory);
            ProcessBuilder builder = new ProcessBuilder(
                    executable.toString(),
                    "serve",
                    "--hostname", "127.0.0.1",
                    "--port", Integer.toString(port),
                    "--print-logs");
            builder.environment().put("XDG_DATA_HOME", configuration.opencodeDataDirectory().toString());
            builder.environment().put("OPENCODE_CONFIG_DIR", configuration.opencodeConfigDirectory().toString());
            // 企业内网客户端使用随 OpenCode 发布的模型快照；禁止启动时访问 models.dev，
            // 避免断网环境首次打开工作区时模型目录阻塞两个远端超时窗口。
            builder.environment().put("OPENCODE_DISABLE_MODELS_FETCH", "true");
            builder.environment().put("TEST_AGENT_INTERNAL_PROXY_BASE_URL", modelRelay.baseUrl());
            builder.environment().put("TEST_AGENT_INTERNAL_PROXY_API_KEY", modelRelay.localToken());
            if (observabilityRelay != null) {
                String generation = "lcg_" + UUID.randomUUID().toString().replace("-", "");
                Path plugin = executable.getParent().getParent()
                        .resolve("plugins/test-agent-observability.mjs")
                        .toAbsolutePath().normalize();
                if (!Files.isRegularFile(plugin)) {
                    throw new IllegalStateException("OpenCode observability plugin is missing");
                }
                builder.environment().put(
                        "OPENCODE_CONFIG_CONTENT",
                        withObservabilityPlugin(
                                builder.environment().get("OPENCODE_CONFIG_CONTENT"),
                                plugin.toUri().toString()));
                builder.environment().put("TEST_AGENT_OBSERVABILITY_BASE_URL", observabilityRelay.baseUrl());
                builder.environment().put("TEST_AGENT_OBSERVABILITY_TOKEN", observabilityRelay.localToken());
                builder.environment().put("TEST_AGENT_OBSERVABILITY_RUNTIME_KIND", "LOCAL_CLIENT");
                builder.environment().put("TEST_AGENT_OBSERVABILITY_GENERATION", generation);
                builder.environment().put(
                        "TEST_AGENT_OBSERVABILITY_CLIENT_INSTANCE_ID",
                        stateStore.read().clientInstanceId());
            }
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logDirectory.resolve("opencode.log").toFile()));
            process = builder.start();
            ProcessHandle handle = process.toHandle();
            Instant startedAt = handle.info().startInstant()
                    .orElseThrow(() -> new IllegalStateException("ProcessHandle did not provide startInstant"));
            recorded = new LocalClientPersistentState.ProcessState(
                    handle.pid(), startedAt, executable.toString(), port);
            persistProcess(recorded);
            if (!waitForHealth(handle, port, HEALTH_TIMEOUT)) {
                stopExact(recorded, handle);
                throw new IllegalStateException("OpenCode loopback health did not become ready");
            }
            return result(true, "RUNNING", handle.pid(), startedAt, port, true,
                    executable.toString(), "启动成功");
        } catch (IOException exception) {
            cleanupFailedStart(process, recorded);
            throw new IllegalStateException("failed to launch OpenCode", exception);
        } catch (RuntimeException exception) {
            cleanupFailedStart(process, recorded);
            throw exception;
        }
    }

    /** 只追加共享插件 URI，保留用户已有 OPENCODE_CONFIG_CONTENT 和其它插件顺序。 */
    String withObservabilityPlugin(String inherited, String pluginUri) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = inherited == null || inherited.isBlank()
                    ? mapper.createObjectNode()
                    : (ObjectNode) mapper.readTree(inherited);
            ArrayNode plugins = root.withArray("plugin");
            boolean present = false;
            for (var plugin : plugins) {
                present |= pluginUri.equals(plugin.asText());
            }
            if (!present) {
                plugins.add(pluginUri);
            }
            return mapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("OPENCODE_CONFIG_CONTENT is invalid", exception);
        }
    }

    /** 当前方法刚创建的 Process 对象可直接终止；成功持久化过的身份记录同时按期望值清除。 */
    private void cleanupFailedStart(
            Process process,
            LocalClientPersistentState.ProcessState recorded) {
        if (process != null && process.isAlive()) {
            process.destroy();
            waitForExit(process.toHandle(), Duration.ofSeconds(2));
            if (process.isAlive()) {
                process.destroyForcibly();
                waitForExit(process.toHandle(), Duration.ofSeconds(2));
            }
        }
        if (recorded != null && (process == null || !process.isAlive())) {
            clearProcess(recorded);
        }
    }

    private void stopExact(LocalClientPersistentState.ProcessState recorded, ProcessHandle handle) {
        IdentityCheck identity = checkIdentity(handle, recorded);
        if (!identity.matches()) {
            return;
        }
        handle.destroy();
        waitForExit(handle, Duration.ofSeconds(2));
        if (handle.isAlive()) {
            handle.destroyForcibly();
            waitForExit(handle, Duration.ofSeconds(2));
        }
        if (!handle.isAlive()) {
            clearProcess(recorded);
        }
    }

    private IdentityCheck checkIdentity(ProcessHandle handle, LocalClientPersistentState.ProcessState recorded) {
        Instant actualStartedAt = handle.info().startInstant().orElse(null);
        if (actualStartedAt == null || !actualStartedAt.equals(recorded.startedAt())) {
            return new IdentityCheck(false, "PID 已被复用，拒绝控制当前进程");
        }
        String command = handle.info().command().orElse(null);
        if (command == null) {
            return new IdentityCheck(false, "无法确认进程可执行文件，拒绝控制");
        }
        try {
            if (!Files.isSameFile(Path.of(command), Path.of(recorded.executable()))) {
                return new IdentityCheck(false, "进程可执行文件与记录不一致，拒绝控制");
            }
        } catch (IOException exception) {
            return new IdentityCheck(false, "进程可执行文件无法核验，拒绝控制");
        }
        String[] arguments = handle.info().arguments().orElse(new String[0]);
        if (!hasArgument(arguments, "serve")
                || !hasOption(arguments, "--hostname", "127.0.0.1")
                || !hasOption(arguments, "--port", Integer.toString(recorded.port()))) {
            return new IdentityCheck(false, "进程启动参数与本地 OpenCode 身份不一致，拒绝控制");
        }
        return new IdentityCheck(true, "identity verified");
    }

    private static boolean hasArgument(String[] arguments, String expected) {
        for (String argument : arguments) {
            if (expected.equalsIgnoreCase(argument)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOption(String[] arguments, String option, String expectedValue) {
        for (int index = 0; index + 1 < arguments.length; index++) {
            if (option.equalsIgnoreCase(arguments[index]) && expectedValue.equals(arguments[index + 1])) {
                return true;
            }
        }
        return false;
    }

    private boolean waitForHealth(ProcessHandle handle, int port, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (handle.isAlive() && Instant.now().isBefore(deadline)) {
            if (healthy(port)) {
                return true;
            }
            sleep(HEALTH_POLL_INTERVAL);
        }
        return handle.isAlive() && healthy(port);
    }

    private boolean healthy(int port) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/global/health"))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300;
        } catch (Exception exception) {
            return false;
        }
    }

    private Path requireExecutable() {
        try {
            Path executable = configuration.opencodeExecutable().toRealPath();
            if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
                throw new IllegalStateException("OpenCode executable is not executable");
            }
            return executable;
        } catch (IOException exception) {
            throw new IllegalStateException("OpenCode executable cannot be resolved", exception);
        }
    }

    private List<Integer> candidatePorts(Integer preferredPort) {
        List<Integer> ports = new ArrayList<>();
        if (preferredPort != null && preferredPort >= configuration.portMin() && preferredPort <= configuration.portMax()) {
            ports.add(preferredPort);
        }
        LocalClientPersistentState.ProcessState previous = stateStore.read().process();
        if (previous != null && !ports.contains(previous.port())) {
            ports.add(previous.port());
        }
        for (int port = configuration.portMin(); port <= configuration.portMax(); port++) {
            if (!ports.contains(port)) {
                ports.add(port);
            }
        }
        return ports;
    }

    private static boolean portAvailable(int port) {
        try (Socket probe = new Socket()) {
            // macOS 允许启用地址复用的通配监听与 127.0.0.1 监听意外共存；先连接可识别所有真实监听者。
            probe.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 200);
            return false;
        } catch (IOException ignored) {
            // 连接被拒绝只表示当前没有监听者；继续用无地址复用的 bind 缩小检查与启动之间的竞态窗口。
        }
        try (ServerSocket socket = new ServerSocket()) {
            // OpenCode 明确监听 127.0.0.1，探测必须使用同一地址族，避免只检查 ::1 后误判端口可用。
            socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private void persistProcess(LocalClientPersistentState.ProcessState process) {
        stateStore.update(state -> new LocalClientPersistentState(
                state.clientInstanceId(), process, state.workspaces()));
    }

    private void clearProcess(LocalClientPersistentState.ProcessState expected) {
        stateStore.update(state -> {
            if (state.process() == null
                    || state.process().processId() != expected.processId()
                    || !state.process().startedAt().equals(expected.startedAt())) {
                return state;
            }
            return new LocalClientPersistentState(state.clientInstanceId(), null, state.workspaces());
        });
    }

    private static void waitForExit(ProcessHandle handle, Duration timeout) {
        try {
            handle.onExit().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // 调用方继续检查 isAlive，并决定是否强制终止或返回失败。
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenCode health wait interrupted", exception);
        }
    }

    private static LocalClientPayloads.LifecycleResult result(
            boolean success,
            String status,
            Long processId,
            Instant processStartedAt,
            Integer port,
            boolean healthy,
            String executable,
            String message) {
        return new LocalClientPayloads.LifecycleResult(
                success, status, processId, processStartedAt, port, healthy, executable, message);
    }

    private record IdentityCheck(boolean matches, String message) {
    }
}
