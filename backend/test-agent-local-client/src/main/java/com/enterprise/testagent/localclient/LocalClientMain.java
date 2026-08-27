package com.enterprise.testagent.localclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** 本地 OpenCode 客户端入口；client key 永远不接受命令行参数。 */
public final class LocalClientMain {

    private LocalClientMain() {
    }

    public static void main(String[] args) {
        Command command = null;
        try {
            command = parseCommand(args);
            run(command, args);
        } catch (Exception exception) {
            LocalClientFailureReporter.report(command, exception, System.err);
            System.exit(1);
        }
    }

    /** 执行已解析命令；顶层统一负责把异常安全地写入持久日志并返回非零退出码。 */
    static void run(Command command, String[] args) throws Exception {
        if (command == Command.VERSION) {
            System.out.println(versionText(LocalClientBuildInfo.current()));
            return;
        }
        if (command == Command.SELF_CHECK) {
            LocalClientSelfCheck.verifyCurrent(Path.of(args[1]), args[2]);
            System.out.println("本地客户端候选版本自检成功");
            return;
        }

        // 日志目录必须在任何可能获取 Logger 的桌面类初始化前完成，否则 Logback 会固定到未展开的占位路径。
        Path logsDirectory = LocalClientPaths.logsDirectory();
        Files.createDirectories(logsDirectory);
        System.setProperty("testagent.localclient.logDir", logsDirectory.toString());

        // 已配置客户端使用菜单栏模式；首次配置必须保留 Dock 和可见窗口，避免用户安装后找不到入口。
        if (!LocalClientFirstRunSetup.requiresFirstRunSetup()) {
            System.setProperty("apple.awt.UIElement", "true");
        }
        // 必须在 apple.awt.UIElement 决策之后初始化 Swing，避免 macOS 首次配置窗口被一并隐藏。
        LocalClientDesktopTheme.install();
        if (!LocalClientFirstRunSetup.ensureConfigured()) {
            return;
        }
        LocalClientConfiguration configuration = LocalClientConfiguration.load();
        LocalClientStateStore stateStore = new LocalClientStateStore();
        if (command == Command.ENROLL) {
            LocalClientRegistrationProbe probe = new LocalClientRegistrationProbe(
                    configuration, stateStore, LocalClientBuildInfo.current());
            LocalClientEnrollment.enroll(
                    LocalClientPaths.configDirectory(),
                    LocalClientEnrollment.systemTerminal(),
                    probe::verify);
            stateStore.clearReEnrollmentRequirement();
            System.out.println("本地客户端接入认证成功");
            return;
        }

        if (stateStore.reEnrollmentRequired()) {
            throw new IllegalStateException("本地客户端凭据已失效，请运行 test-agent-local-client enroll 重新接入");
        }
        LocalClientCredentialFile.Credentials credentials = LocalClientCredentialFile.read();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        LocalClientBuildInfo buildInfo = LocalClientBuildInfo.current();
        LocalClientPublicCapabilityStore publicCapabilities =
                new LocalClientPublicCapabilityStore(stateStore.stateDirectory());
        publicCapabilities.initializeBaseline(configuration, buildInfo);
        LocalWorkspaceRegistry workspaceRegistry = new LocalWorkspaceRegistry(stateStore);
        LocalObservabilitySettings observabilitySettings = LocalObservabilitySettings.load();
        try (LocalModelRelay modelRelay = new LocalModelRelay(configuration);
             LocalObservabilityRelay observabilityRelay = new LocalObservabilityRelay(stateStore, observabilitySettings)) {
            OpencodeProcessSupervisor supervisor = new OpencodeProcessSupervisor(
                    configuration, stateStore, modelRelay, observabilityRelay, publicCapabilities);
            if (configuration.selfUpdateConfigured() && buildInfo.managedRelease()) {
                LocalClientUpdateMarkerStore markerStore =
                        new LocalClientUpdateMarkerStore(stateStore.stateDirectory());
                int activationExitCode = LocalClientUpdateActivation.production(
                                buildInfo.clientVersion(), markerStore, () -> supervisor.start(null))
                        .activateIfPending();
                if (activationExitCode != 0) {
                    supervisor.stop();
                    System.exit(activationExitCode);
                }
            }
            recoverPublicCapabilityActivation(publicCapabilities, supervisor);
            LocalClientFileRpcHandler fileRpcHandler = new LocalClientFileRpcHandler(
                    workspaceRegistry, objectMapper);
            LocalClientConnection connection = new LocalClientConnection(
                    configuration,
                    credentials,
                    stateStore,
                    supervisor,
                    modelRelay,
                    fileRpcHandler,
                    observabilityRelay,
                    observabilitySettings,
                    publicCapabilities);
            LocalClientTray tray = LocalClientTray.install(configuration, connection);
            try (connection; tray) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    tray.close();
                    connection.close();
                }, "local-client-shutdown"));
                int exitCode = connection.runForever();
                if (exitCode != 0) {
                    System.exit(exitCode);
                }
            }
        }
    }

    /**
     * 客户端可能在原子切换后、服务端 ACK 前退出；启动时先用真实 OpenCode 验证新目录，
     * 失败则恢复 previousDigest。下载阶段中断只终止该次尝试，不触碰当前能力版本。
     */
    static void recoverPublicCapabilityActivation(
            LocalClientPublicCapabilityStore store,
            OpencodeProcessSupervisor supervisor) {
        LocalClientPublicCapabilityStore.State state = store.snapshot();
        if ("PENDING".equals(state.status()) || "DOWNLOADING".equals(state.status())) {
            store.recordStatus("FAILED", "CLIENT_RESTARTED_DURING_UPDATE");
            return;
        }
        if (!"APPLYING".equals(state.status())) {
            return;
        }
        var health = supervisor.reloadPublicCapabilities(true);
        if (health.success() && health.opencodeHealthy() && supervisor.validatePublicCapabilityCatalog()) {
            store.completeActivation();
            return;
        }
        org.slf4j.LoggerFactory.getLogger(LocalClientMain.class).warn(
                "public_capability_activation_validation_failed processStatus={} healthy={} message={}",
                health.processStatus(), health.opencodeHealthy(), health.message());
        try {
            store.rollback(state.previousDigest(), "OPENCODE_ACTIVATION_FAILED");
            var restored = supervisor.reloadPublicCapabilities(true);
            if (!restored.success() || !restored.opencodeHealthy()
                    || !supervisor.validatePublicCapabilityCatalog()) {
                throw new IllegalStateException("公共能力回滚后 OpenCode 未恢复健康: " + restored.message());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("公共能力崩溃恢复失败", exception);
        }
    }

    static String versionText(LocalClientBuildInfo buildInfo) {
        return "test-agent-local-client " + buildInfo.clientVersion();
    }

    static Command parseCommand(String[] args) {
        if (Arrays.stream(args).anyMatch(argument -> argument.toLowerCase().contains("key"))) {
            throw new IllegalArgumentException("client key must not be passed through command line arguments");
        }
        if (args.length == 0) {
            return Command.RUN;
        }
        if (args.length == 1 && "--version".equals(args[0])) {
            return Command.VERSION;
        }
        if (args.length == 1 && "enroll".equals(args[0])) {
            return Command.ENROLL;
        }
        if (args.length == 3 && "self-check".equals(args[0])) {
            return Command.SELF_CHECK;
        }
        throw new IllegalArgumentException("unsupported command line arguments");
    }

    enum Command {
        RUN,
        VERSION,
        ENROLL,
        SELF_CHECK
    }
}
