package com.enterprise.testagent.localclient;

import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 本地 OpenCode 监管器；PID、实际启动时间和可执行文件全部匹配后才允许停止。 */
final class OpencodeProcessSupervisor {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpencodeProcessSupervisor.class);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HEALTH_POLL_INTERVAL = Duration.ofMillis(250);
    private static final Duration CATALOG_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_MANAGED_MODEL_CONFIG_BYTES = 1024 * 1024;
    private static final Set<String> MANAGED_MODEL_CONFIG_FIELDS = Set.of(
            "model", "small_model", "enabled_providers", "provider");
    private final LocalClientConfiguration configuration;
    private final LocalClientStateStore stateStore;
    private final LocalModelRelay modelRelay;
    private final LocalObservabilityRelay observabilityRelay;
    private final LocalClientPublicCapabilityStore publicCapabilityStore;
    private final LocalBrowserRelay browserRelay;
    private final HttpClient httpClient = loopbackHttpClient();
    private volatile String managedModelConfigContent;
    private volatile boolean managedModelRestartRequired;
    private volatile boolean managedRtkEnabled;
    private volatile boolean managedRtkRestartRequired;
    /** 本地 V2 server 的 loopback Basic Auth 密码，与当前受管进程同生命周期。 */
    private volatile String opencodeServerPassword;

    OpencodeProcessSupervisor(
            LocalClientConfiguration configuration,
            LocalClientStateStore stateStore,
            LocalModelRelay modelRelay) {
        this(configuration, stateStore, modelRelay, null, null, null);
    }

    OpencodeProcessSupervisor(
            LocalClientConfiguration configuration,
            LocalClientStateStore stateStore,
            LocalModelRelay modelRelay,
            LocalObservabilityRelay observabilityRelay) {
        this(configuration, stateStore, modelRelay, observabilityRelay, null, null);
    }

    OpencodeProcessSupervisor(
            LocalClientConfiguration configuration,
            LocalClientStateStore stateStore,
            LocalModelRelay modelRelay,
            LocalObservabilityRelay observabilityRelay,
            LocalClientPublicCapabilityStore publicCapabilityStore) {
        this(configuration, stateStore, modelRelay, observabilityRelay, publicCapabilityStore, null);
    }

    OpencodeProcessSupervisor(
            LocalClientConfiguration configuration,
            LocalClientStateStore stateStore,
            LocalModelRelay modelRelay,
            LocalObservabilityRelay observabilityRelay,
            LocalClientPublicCapabilityStore publicCapabilityStore,
            LocalBrowserRelay browserRelay) {
        this.configuration = configuration;
        this.stateStore = stateStore;
        this.modelRelay = modelRelay;
        this.observabilityRelay = observabilityRelay;
        this.publicCapabilityStore = publicCapabilityStore;
        this.browserRelay = browserRelay;
    }

    /**
     * 保存服务端下发的无密钥模型配置，供下一次受管启动写入 OPENCODE_CONFIG_CONTENT。
     * 配置只允许模型/provider 根字段，避免连接载荷改变 Tool、插件或文件系统权限。
     */
    synchronized void configureManagedModel(Map<String, Object> config) {
        String validated = validateManagedModelConfig(config);
        if (!java.util.Objects.equals(managedModelConfigContent, validated)) {
            managedModelConfigContent = validated;
            // 客户端升级或重连时可能继承仍在运行的旧 OpenCode；下一次 start 必须重启后再报告就绪。
            managedModelRestartRequired = true;
            LOGGER.info("local_opencode_managed_model_config_changed configPresent={} restartRequired=true",
                    validated != null);
        }
    }

    /** 保存服务端下发的 RTK 开关；只接受布尔开关，不允许通过协议注入路径或插件。 */
    synchronized void configureManagedRuntime(Map<String, Object> config) {
        boolean enabled = validateManagedRtkConfig(config);
        if (managedRtkEnabled != enabled) {
            managedRtkEnabled = enabled;
            managedRtkRestartRequired = true;
            LOGGER.info("local_opencode_managed_rtk_config_changed enabled={} restartRequired=true", enabled);
        }
    }

    synchronized LocalClientPayloads.LifecycleResult start(Integer preferredPort) {
        long startedNanos = System.nanoTime();
        LocalClientPayloads.LifecycleResult current = status();
        LOGGER.info("local_opencode_start_requested preferredPort={} currentStatus={} currentProcessId={} currentPort={} currentHealthy={} modelRestartRequired={} rtkEnabled={} rtkRestartRequired={}",
                preferredPort, current.processStatus(), current.processId(), current.opencodePort(),
                current.opencodeHealthy(), managedModelRestartRequired, managedRtkEnabled, managedRtkRestartRequired);
        if (current.success() && "RUNNING".equals(current.processStatus()) && current.opencodeHealthy()) {
            if (!managedModelRestartRequired && !managedRtkRestartRequired) {
                LOGGER.info("local_opencode_start_reused processId={} port={} durationMs={}",
                        current.processId(), current.opencodePort(),
                        LocalClientDiagnostics.elapsedMillis(startedNanos));
                return current;
            }
            LocalClientPayloads.LifecycleResult stopped = stop();
            if (!stopped.success()) {
                return stopped;
            }
            current = stopped;
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
                LOGGER.debug("local_opencode_start_port_skipped port={} reason=PORT_UNAVAILABLE", port);
                continue;
            }
            try {
                LocalClientPayloads.LifecycleResult started = startOnPort(executable, port);
                LOGGER.info("local_opencode_start_completed success={} processStatus={} processId={} port={} healthy={} durationMs={}",
                        started.success(), started.processStatus(), started.processId(), started.opencodePort(),
                        started.opencodeHealthy(), LocalClientDiagnostics.elapsedMillis(startedNanos));
                return started;
            } catch (RuntimeException exception) {
                LOGGER.warn("local_opencode_start_attempt_failed port={} rootFailureType={} durationMs={}",
                        port, LocalClientDiagnostics.rootFailureType(exception),
                        LocalClientDiagnostics.elapsedMillis(startedNanos));
                lastFailure = exception;
            }
        }
        String message = lastFailure == null ? "没有可用的本地 OpenCode 端口" : "本地 OpenCode 启动失败";
        LOGGER.warn("local_opencode_start_failed preferredPort={} candidateCount={} failureCode={} rootFailureType={} durationMs={}",
                preferredPort, ports.size(), lastFailure == null ? "NO_PORT_AVAILABLE" : "PROCESS_START_FAILED",
                lastFailure == null ? "NONE" : LocalClientDiagnostics.rootFailureType(lastFailure),
                LocalClientDiagnostics.elapsedMillis(startedNanos));
        return result(false, "FAILED", null, null, null, false, executable.toString(), message);
    }

    synchronized LocalClientPayloads.LifecycleResult restart(Integer preferredPort) {
        LOGGER.info("local_opencode_restart_requested preferredPort={}", preferredPort);
        LocalClientPayloads.LifecycleResult stopped = stop();
        if (!stopped.success()) {
            return stopped;
        }
        return start(preferredPort);
    }

    synchronized LocalClientPayloads.LifecycleResult stop() {
        long startedNanos = System.nanoTime();
        LocalClientPersistentState.ProcessState recorded = stateStore.read().process();
        if (browserRelay != null) {
            browserRelay.stopBrowser();
        }
        if (recorded == null) {
            LOGGER.info("local_opencode_stop_completed source=no_record durationMs={}",
                    LocalClientDiagnostics.elapsedMillis(startedNanos));
            return result(true, "STOPPED", null, null, null, false, requireExecutable().toString(), "已停止");
        }
        LOGGER.info("local_opencode_stop_started processId={} port={}", recorded.processId(), recorded.port());
        ProcessHandle handle = ProcessHandle.of(recorded.processId()).orElse(null);
        if (handle == null || !handle.isAlive()) {
            // 权威 PID 已退出时，端口上的任何新进程都不再属于本客户端：只清过期记录，绝不控制陌生进程。
            // 后续 start 会通过受控端口探测跳过占用端口，避免陈旧状态永久阻断自动恢复。
            clearProcess(recorded);
            LOGGER.info("local_opencode_stop_completed processId={} port={} source=stale_record durationMs={}",
                    recorded.processId(), recorded.port(), LocalClientDiagnostics.elapsedMillis(startedNanos));
            return result(true, "STOPPED", null, null, recorded.port(), false, recorded.executable(), "原进程已退出");
        }
        IdentityCheck identity = checkIdentity(handle, recorded);
        if (!identity.matches()) {
            LOGGER.warn("local_opencode_stop_failed processId={} port={} failureCode=PROCESS_IDENTITY_MISMATCH durationMs={}",
                    recorded.processId(), recorded.port(), LocalClientDiagnostics.elapsedMillis(startedNanos));
            return result(false, "FAILED", recorded.processId(), recorded.startedAt(), recorded.port(),
                    healthy(recorded.port()), recorded.executable(), identity.message());
        }
        handle.destroy();
        waitForExit(handle, Duration.ofSeconds(5));
        if (handle.isAlive()) {
            LOGGER.info("local_opencode_stop_force_requested processId={} port={}",
                    recorded.processId(), recorded.port());
            handle.destroyForcibly();
            waitForExit(handle, Duration.ofSeconds(5));
        }
        if (handle.isAlive() || healthy(recorded.port())) {
            LOGGER.warn("local_opencode_stop_failed processId={} port={} failureCode=STOP_CONFIRMATION_FAILED durationMs={}",
                    recorded.processId(), recorded.port(), LocalClientDiagnostics.elapsedMillis(startedNanos));
            return result(false, "FAILED", recorded.processId(), recorded.startedAt(), recorded.port(),
                    healthy(recorded.port()), recorded.executable(), "本地 OpenCode 停止确认失败");
        }
        clearProcess(recorded);
        LOGGER.info("local_opencode_stop_completed processId={} port={} source=managed_stop durationMs={}",
                recorded.processId(), recorded.port(), LocalClientDiagnostics.elapsedMillis(startedNanos));
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

    /** Agent/Skill 只 dispose；Tool/依赖变化必须完整重启，二者都以健康检查作为成功条件。 */
    synchronized LocalClientPayloads.LifecycleResult reloadPublicCapabilities(boolean requiresRestart) {
        long startedNanos = System.nanoTime();
        LocalClientPayloads.LifecycleResult current = status();
        LOGGER.info("local_opencode_public_capability_reload_started requiresRestart={} currentStatus={} processId={} port={}",
                requiresRestart, current.processStatus(), current.processId(), current.opencodePort());
        if (requiresRestart) {
            LocalClientPayloads.LifecycleResult restarted = restart(current.opencodePort());
            LOGGER.info("local_opencode_public_capability_reload_completed mode=restart success={} status={} healthy={} durationMs={}",
                    restarted.success(), restarted.processStatus(), restarted.opencodeHealthy(),
                    LocalClientDiagnostics.elapsedMillis(startedNanos));
            return restarted;
        }
        if (!current.success() || current.opencodePort() == null || !current.opencodeHealthy()) {
            LocalClientPayloads.LifecycleResult started = start(current.opencodePort());
            LOGGER.info("local_opencode_public_capability_reload_completed mode=start success={} status={} healthy={} durationMs={}",
                    started.success(), started.processStatus(), started.opencodeHealthy(),
                    LocalClientDiagnostics.elapsedMillis(startedNanos));
            return started;
        }
        try {
            HttpRequest.Builder reloadBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + current.opencodePort() + "/api/location/reload"))
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.noBody());
            String reloadAuth = basicAuthHeader();
            if (reloadAuth != null) reloadBuilder.header("Authorization", reloadAuth);
            HttpRequest request = reloadBuilder.build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("local_opencode_public_capability_reload_failed mode=dispose status={} durationMs={}",
                        response.statusCode(), LocalClientDiagnostics.elapsedMillis(startedNanos));
                return result(false, "FAILED", current.processId(), current.processStartedAt(), current.opencodePort(),
                        false, current.executable(), "OpenCode dispose 失败");
            }
            LocalClientPayloads.LifecycleResult reloaded = status();
            LOGGER.info("local_opencode_public_capability_reload_completed mode=dispose success={} status={} healthy={} durationMs={}",
                    reloaded.success(), reloaded.processStatus(), reloaded.opencodeHealthy(),
                    LocalClientDiagnostics.elapsedMillis(startedNanos));
            return reloaded;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("local_opencode_public_capability_reload_failed mode=dispose failureCode=INTERRUPTED durationMs={}",
                    LocalClientDiagnostics.elapsedMillis(startedNanos));
            return result(false, "FAILED", current.processId(), current.processStartedAt(), current.opencodePort(),
                    false, current.executable(), "OpenCode dispose 被中断");
        } catch (IOException exception) {
            LOGGER.warn("local_opencode_public_capability_reload_failed mode=dispose failureCode=CONNECTION_FAILED rootFailureType={} durationMs={}",
                    LocalClientDiagnostics.rootFailureType(exception),
                    LocalClientDiagnostics.elapsedMillis(startedNanos));
            return result(false, "FAILED", current.processId(), current.processStartedAt(), current.opencodePort(),
                    false, current.executable(), "OpenCode dispose 连接失败");
        }
    }

    /**
     * 激活成功不仅要求进程存活，还要确保 OpenCode 已重新加载 Agent、Skill command 与 Tool 目录。
     * 包内目录及 manifest 完整性已经由能力包 Store 在切换前完成校验。
     */
    synchronized boolean validatePublicCapabilityCatalog() {
        LocalClientPayloads.LifecycleResult current = status();
        if (!current.success() || !current.opencodeHealthy() || current.opencodePort() == null) {
            return false;
        }
        // Tool 首次装载会初始化完整配置，先给它独立的冷启动窗口；后续 Agent/Skill 查询复用同一实例。
        return catalogAvailable(current.opencodePort(), "/api/config")
                && catalogAvailable(current.opencodePort(), "/api/agent")
                && catalogAvailable(current.opencodePort(), "/api/command");
    }

    private boolean catalogAvailable(int port, String path) {
        Instant startedAt = Instant.now();
        try {
            Path validationDirectory = configuration.opencodeDataDirectory()
                    .resolve("public-capability-healthcheck").toAbsolutePath().normalize();
            Files.createDirectories(validationDirectory);
            HttpRequest.Builder catalogBuilder = HttpRequest.newBuilder()
                    .uri(catalogUri(port, path, validationDirectory))
                    .timeout(CATALOG_TIMEOUT)
                    .GET();
            String catalogAuth = basicAuthHeader();
            if (catalogAuth != null) catalogBuilder.header("Authorization", catalogAuth);
            HttpRequest request = catalogBuilder.build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            boolean available = response.statusCode() >= 200 && response.statusCode() < 300;
            LOGGER.info("local_opencode_catalog_check path={} status={} available={} durationMs={}",
                    path, response.statusCode(), available, Duration.between(startedAt, Instant.now()).toMillis());
            return available;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("local_opencode_catalog_check_interrupted path={} durationMs={}",
                    path, Duration.between(startedAt, Instant.now()).toMillis());
            return false;
        } catch (IOException exception) {
            LOGGER.warn("local_opencode_catalog_check_failed path={} durationMs={} errorType={}",
                    path, Duration.between(startedAt, Instant.now()).toMillis(),
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    /** OpenCode 目录型接口必须携带受控目录，避免默认使用大型当前工作目录拖慢首次能力验证。 */
    static URI catalogUri(int port, String path, Path validationDirectory) {
        String encodedDirectory = URLEncoder.encode(
                validationDirectory.toAbsolutePath().normalize().toString(), StandardCharsets.UTF_8);
        return URI.create("http://127.0.0.1:" + port + path + "?location%5Bdirectory%5D=" + encodedDirectory);
    }

    static HttpClient loopbackHttpClient() {
        return HttpClient.newBuilder()
                // OpenCode 2.0.18 的明文 loopback 目录接口不完整支持 JDK h2c upgrade，固定 HTTP/1.1。
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(1))
                .build();
    }

    private LocalClientPayloads.LifecycleResult startOnPort(Path executable, int port) {
        long startedNanos = System.nanoTime();
        Process process = null;
        LocalClientPersistentState.ProcessState recorded = null;
        try {
            Path configDirectory = publicCapabilityStore == null
                    ? configuration.opencodeConfigDirectory()
                    : publicCapabilityStore.activeConfigDirectory(configuration.opencodeConfigDirectory());
            if (!Files.isSymbolicLink(configDirectory)) {
                Files.createDirectories(configDirectory);
            }
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
            String serverPassword = System.getenv("TEST_AGENT_OPENCODE_SERVER_PASSWORD");
            if (serverPassword == null || serverPassword.isBlank()) serverPassword = UUID.randomUUID().toString();
            opencodeServerPassword = serverPassword;
            builder.environment().put("OPENCODE_PASSWORD", serverPassword);
            builder.environment().put("OPENCODE_CONFIG_DIR", configDirectory.toString());
            enforceOfflineRuntime(builder.environment());
            builder.environment().put("TEST_AGENT_INTERNAL_PROXY_BASE_URL", modelRelay.baseUrl());
            builder.environment().put("TEST_AGENT_INTERNAL_PROXY_API_KEY", modelRelay.localToken());
            if (browserRelay != null) {
                builder.environment().put("TEST_AGENT_LOCAL_BROWSER_BASE_URL", browserRelay.baseUrl());
                builder.environment().put("TEST_AGENT_LOCAL_BROWSER_TOKEN", browserRelay.localToken());
            }
            String configContent = mergeManagedModelConfig(
                    builder.environment().get("OPENCODE_CONFIG_CONTENT"),
                    managedModelConfigContent);
            if (configContent != null) {
                builder.environment().put("OPENCODE_CONFIG_CONTENT", configContent);
            }
            configureRtkRuntime(builder.environment(), executable);
            if (observabilityRelay != null) {
                String generation = "lcg_" + UUID.randomUUID().toString().replace("-", "");
                Path plugin = executable.getParent().getParent()
                        .resolve("plugins/test-agent-observability")
                        .toAbsolutePath().normalize();
                if (!Files.isRegularFile(plugin.resolve("index.mjs"))) {
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
            LOGGER.info("local_opencode_process_launch_started port={} configSource={} observabilityEnabled={} offlineDependencies=true logFile=opencode.log",
                    port, publicCapabilityStore == null ? "configured_directory" : "signed_public_capability",
                    observabilityRelay != null);
            process = builder.start();
            ProcessHandle handle = process.toHandle();
            Instant startedAt = handle.info().startInstant()
                    .orElseThrow(() -> new IllegalStateException("ProcessHandle did not provide startInstant"));
            recorded = new LocalClientPersistentState.ProcessState(
                    handle.pid(), startedAt, executable.toString(), port);
            persistProcess(recorded);
            LOGGER.info("local_opencode_process_started processId={} port={} waitingForHealth=true durationMs={}",
                    handle.pid(), port, LocalClientDiagnostics.elapsedMillis(startedNanos));
            if (!waitForHealth(handle, port, HEALTH_TIMEOUT)) {
                stopExact(recorded, handle);
                throw new IllegalStateException("OpenCode loopback health did not become ready");
            }
            managedModelRestartRequired = false;
            managedRtkRestartRequired = false;
            LOGGER.info("local_opencode_process_healthy processId={} port={} healthTimeoutSeconds={} durationMs={}",
                    handle.pid(), port, HEALTH_TIMEOUT.toSeconds(),
                    LocalClientDiagnostics.elapsedMillis(startedNanos));
            return result(true, "RUNNING", handle.pid(), startedAt, port, true,
                    executable.toString(), "启动成功");
        } catch (IOException exception) {
            cleanupFailedStart(process, recorded);
            LOGGER.warn("local_opencode_process_launch_failed port={} rootFailureType={} durationMs={}",
                    port, LocalClientDiagnostics.rootFailureType(exception),
                    LocalClientDiagnostics.elapsedMillis(startedNanos));
            throw new IllegalStateException("failed to launch OpenCode", exception);
        } catch (RuntimeException exception) {
            cleanupFailedStart(process, recorded);
            LOGGER.warn("local_opencode_process_launch_failed port={} rootFailureType={} durationMs={}",
                    port, LocalClientDiagnostics.rootFailureType(exception),
                    LocalClientDiagnostics.elapsedMillis(startedNanos));
            throw exception;
        }
    }

    /**
     * 企业客户端的 OpenCode 只能消费 release 和签名公共能力包中的依赖，禁止向公网补装。
     * OpenCode 会同时扫描全局配置、旧配置和受管配置目录；任一目录缺少 node_modules 时，
     * npm 离线模式必须让后台依赖检查立即结束，避免 Tool/插件目录等待公网连接超时。
     */
    static void enforceOfflineRuntime(Map<String, String> environment) {
        // npm 配置环境变量大小写不敏感，先移除父进程遗留的相反值，保证离线约束唯一且确定。
        environment.keySet().removeIf("npm_config_offline"::equalsIgnoreCase);
        environment.put("npm_config_offline", "true");
        // 企业内网客户端使用随 OpenCode 发布的模型快照，禁止启动时访问 models.dev。
        environment.put("OPENCODE_DISABLE_MODELS_FETCH", "true");
    }

    /** 只追加共享插件 URI，保留用户已有 OPENCODE_CONFIG_CONTENT 和其它插件顺序。 */
    String withObservabilityPlugin(String inherited, String pluginUri) {
        return withPlugin(inherited, pluginUri);
    }

    /** 追加插件 URI 时复用同一份 JSON 校验和去重逻辑，避免 RTK 引入第二套配置合并器。 */
    String withPlugin(String inherited, String pluginUri) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = inherited == null || inherited.isBlank()
                    ? mapper.createObjectNode()
                    : (ObjectNode) mapper.readTree(inherited);
            ArrayNode plugins = root.withArray("plugins");
            // V1 配置中的字符串插件仅在读入时迁移，写回统一使用 V2 {package} tuple。
            if (root.has("plugin")) {
                for (var legacy : root.withArray("plugin")) {
                    if (legacy.isTextual()) {
                        ObjectNode entry = mapper.createObjectNode();
                        entry.put("package", legacy.asText());
                        plugins.add(entry);
                    }
                }
                root.remove("plugin");
            }
            boolean present = false;
            for (var plugin : plugins) {
                present |= pluginUri.equals(plugin.isTextual() ? plugin.asText() : plugin.path("package").asText());
            }
            if (!present) {
                ObjectNode entry = mapper.createObjectNode();
                entry.put("package", pluginUri);
                plugins.add(entry);
            }
            return mapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("OPENCODE_CONFIG_CONTENT is invalid", exception);
        }
    }

    private void configureRtkRuntime(Map<String, String> environment, Path executable) {
        environment.put("TEST_AGENT_RTK_ENABLED", Boolean.toString(managedRtkEnabled));
        if (!managedRtkEnabled) {
            environment.remove("TEST_AGENT_RTK_BIN");
            environment.remove("RTK_TELEMETRY_DISABLED");
            environment.remove("RTK_RECALL");
            return;
        }
        Path runtimeRoot = executable.toAbsolutePath().normalize().getParent() == null
                ? executable.toAbsolutePath().normalize()
                : executable.toAbsolutePath().normalize().getParent().getParent();
        Path plugin = runtimeRoot.resolve("plugins/test-agent-rtk").normalize();
        String executableName = executable.getFileName() == null
                ? ""
                : executable.getFileName().toString().toLowerCase(Locale.ROOT);
        Path binary = executable.toAbsolutePath().normalize().getParent()
                .resolve(executableName.endsWith(".exe") ? "rtk.exe" : "rtk")
                .normalize();
        if (!Files.isRegularFile(plugin.resolve("index.mjs")) || !Files.isExecutable(binary)) {
            throw new IllegalStateException("OpenCode RTK runtime is missing");
        }
        environment.put("TEST_AGENT_RTK_BIN", binary.toString());
        environment.put("RTK_TELEMETRY_DISABLED", "1");
        environment.put("RTK_RECALL", "0");
        String current = environment.get("OPENCODE_CONFIG_CONTENT");
        environment.put("OPENCODE_CONFIG_CONTENT", withPlugin(current, plugin.toUri().toString()));
    }

    static boolean validateManagedRtkConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) return false;
        if (config.keySet().stream().anyMatch(key -> !"rtkEnabled".equals(key))) {
            throw new IllegalArgumentException("managed runtime config field is not allowed");
        }
        Object value = config.get("rtkEnabled");
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("managed runtime config rtkEnabled must be boolean");
        }
        return (Boolean) value;
    }

    static String validateManagedModelConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.valueToTree(config);
            root.fieldNames().forEachRemaining(field -> {
                if (!MANAGED_MODEL_CONFIG_FIELDS.contains(field)) {
                    throw new IllegalArgumentException("managed model config field is not allowed: " + field);
                }
            });
            if (!root.path("model").isTextual()
                    || !root.path("small_model").isTextual()
                    || !root.path("enabled_providers").isArray()
                    || !root.path("provider").isObject()) {
                throw new IllegalArgumentException("managed model config structure is invalid");
            }
            String json = mapper.writeValueAsString(root);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_MANAGED_MODEL_CONFIG_BYTES) {
                throw new IllegalArgumentException("managed model config exceeds size limit");
            }
            return json;
        } catch (IOException exception) {
            throw new IllegalArgumentException("managed model config is invalid", exception);
        }
    }

    /** 服务端受管模型字段覆盖父进程同名值，其它本地覆盖项继续保留。 */
    static String mergeManagedModelConfig(String inherited, String managed) {
        if (managed == null || managed.isBlank()) {
            return inherited;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = inherited == null || inherited.isBlank()
                    ? mapper.createObjectNode()
                    : requireObject(mapper, inherited);
            root.setAll(requireObject(mapper, managed));
            return mapper.writeValueAsString(root);
        } catch (IOException exception) {
            throw new IllegalStateException("OPENCODE_CONFIG_CONTENT is invalid", exception);
        }
    }

    private static ObjectNode requireObject(ObjectMapper mapper, String json) throws IOException {
        var node = mapper.readTree(json);
        if (!(node instanceof ObjectNode object)) {
            throw new IllegalArgumentException("OPENCODE_CONFIG_CONTENT root must be an object");
        }
        return object;
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
            HttpRequest.Builder healthBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/info"))
                    .timeout(Duration.ofSeconds(1))
                    .GET();
            String healthAuth = basicAuthHeader();
            if (healthAuth != null) healthBuilder.header("Authorization", healthAuth);
            HttpRequest request = healthBuilder.build();
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300;
        } catch (Exception exception) {
            return false;
        }
    }

    private String basicAuthHeader() {
        String password = opencodeServerPassword;
        if (password == null || password.isBlank()) return null;
        return "Basic " + Base64.getEncoder().encodeToString(
                ("opencode:" + password).getBytes(StandardCharsets.UTF_8));
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
