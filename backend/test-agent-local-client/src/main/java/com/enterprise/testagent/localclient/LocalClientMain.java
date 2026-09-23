package com.enterprise.testagent.localclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        LocalClientDiagnostics.ensureSessionId();
        Logger logger = LoggerFactory.getLogger(LocalClientMain.class);
        long commandStartedNanos = System.nanoTime();
        LocalClientBuildInfo startupBuildInfo = LocalClientBuildInfo.current();
        LocalClientPlatform startupPlatform = LocalClientPlatform.current();
        logger.info(
                "local_client_command_started command={} clientVersion={} platform={} architecture={} managedRelease={}",
                command.name(), startupBuildInfo.clientVersion(), startupPlatform.platform(),
                startupPlatform.architecture(), startupBuildInfo.managedRelease());

        try {
            // 已配置客户端使用菜单栏模式；首次配置必须保留 Dock 和可见窗口，避免用户安装后找不到入口。
            if (!LocalClientFirstRunSetup.requiresFirstRunSetup()) {
                System.setProperty("apple.awt.UIElement", "true");
            }
            // 必须在 apple.awt.UIElement 决策之后初始化 Swing，避免 macOS 首次配置窗口被一并隐藏。
            LocalClientDesktopTheme.install();
            if (!LocalClientFirstRunSetup.ensureConfigured()) {
                logger.info("local_client_configuration_cancelled command={} durationMs={}",
                        command.name(), LocalClientDiagnostics.elapsedMillis(commandStartedNanos));
                return;
            }
            LocalClientConfiguration configuration = LocalClientConfiguration.load();
            LocalClientStateStore stateStore = new LocalClientStateStore();
            logger.info(
                    "local_client_configuration_loaded command={} insecureControl={} selfUpdateConfigured={} portRange={}..{}",
                    command.name(), configuration.allowInsecureControl(), configuration.selfUpdateConfigured(),
                    configuration.portMin(), configuration.portMax());
            if (command == Command.ENROLL) {
                logger.info("local_client_enrollment_started clientVersion={}", startupBuildInfo.clientVersion());
                LocalClientRegistrationProbe probe = new LocalClientRegistrationProbe(
                        configuration, stateStore, startupBuildInfo);
                LocalClientEnrollment.enroll(
                        LocalClientPaths.configDirectory(),
                        LocalClientEnrollment.systemTerminal(),
                        probe::verify);
                stateStore.clearReEnrollmentRequirement();
                logger.info("local_client_enrollment_completed clientVersion={} durationMs={}",
                        startupBuildInfo.clientVersion(), LocalClientDiagnostics.elapsedMillis(commandStartedNanos));
                System.out.println("本地客户端接入认证成功");
                return;
            }

            if (stateStore.reEnrollmentRequired()) {
                throw new IllegalStateException("本地客户端凭据已失效，请运行 test-agent-local-client enroll 重新接入");
            }
            LocalClientCredentialFile.Credentials credentials = LocalClientCredentialFile.read();
            ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
            LocalClientBuildInfo buildInfo = startupBuildInfo;
            LocalClientPublicCapabilityStore publicCapabilities =
                    new LocalClientPublicCapabilityStore(stateStore.stateDirectory());
            publicCapabilities.initializeBaseline(configuration, buildInfo);
            LocalClientPublicCapabilityStore.State capabilityState = publicCapabilities.snapshot();
            logger.info("local_client_public_capability_ready status={} activeDigestPresent={} pendingDigestPresent={}",
                    capabilityState.status(), capabilityState.activeDigest() != null, capabilityState.pendingDigest() != null);
            LocalWorkspaceRegistry workspaceRegistry = new LocalWorkspaceRegistry(stateStore);
            LocalObservabilitySettings observabilitySettings = LocalObservabilitySettings.load();
            logger.info("local_client_runtime_initializing observabilityEnabled={} clientInstanceId={}",
                    observabilitySettings.maxInFlight() > 0, stateStore.read().clientInstanceId());
            try (LocalModelRelay modelRelay = new LocalModelRelay(configuration);
                 LocalObservabilityRelay observabilityRelay = new LocalObservabilityRelay(stateStore, observabilitySettings);
                 LocalBrowserRelay browserRelay = LocalBrowserSettings.supportedPlatform(startupPlatform)
                         ? new LocalBrowserRelay(
                                 new LocalBrowserSupervisor(LocalBrowserSettings.production(), objectMapper), objectMapper)
                         : null) {
                OpencodeProcessSupervisor supervisor = new OpencodeProcessSupervisor(
                        configuration, stateStore, modelRelay, observabilityRelay, publicCapabilities, browserRelay);
                if (configuration.selfUpdateConfigured() && buildInfo.managedRelease()) {
                    LocalClientUpdateMarkerStore markerStore =
                            new LocalClientUpdateMarkerStore(stateStore.stateDirectory());
                    logger.info("local_client_pending_activation_check clientVersion={}", buildInfo.clientVersion());
                    int activationExitCode = LocalClientUpdateActivation.production(
                                    buildInfo.clientVersion(), markerStore, () -> supervisor.start(null))
                            .activateIfPending();
                    if (activationExitCode != 0) {
                        logger.warn("local_client_pending_activation_failed clientVersion={} exitCode={}",
                                buildInfo.clientVersion(), activationExitCode);
                        supervisor.stop();
                        System.exit(activationExitCode);
                    }
                }
                recoverPublicCapabilityActivation(publicCapabilities, supervisor);
                LocalClientFileRpcHandler fileRpcHandler = new LocalClientFileRpcHandler(
                        workspaceRegistry, objectMapper, new LocalGitAccessChecker(), publicCapabilities);
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
                LocalClientTray tray = LocalClientTray.install(configuration, connection, browserRelay);
                try (connection; tray) {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        logger.info("local_client_shutdown_hook_started");
                        tray.close();
                        connection.close();
                    }, "local-client-shutdown"));
                    logger.info("local_client_runtime_started clientVersion={} startupDurationMs={}",
                            buildInfo.clientVersion(), LocalClientDiagnostics.elapsedMillis(commandStartedNanos));
                    int exitCode = connection.runForever();
                    logger.info("local_client_runtime_stopped clientVersion={} exitCode={} totalDurationMs={}",
                            buildInfo.clientVersion(), exitCode,
                            LocalClientDiagnostics.elapsedMillis(commandStartedNanos));
                    if (exitCode != 0) {
                        System.exit(exitCode);
                    }
                }
            }
        } catch (Exception exception) {
            logger.error("local_client_command_aborted command={} durationMs={} rootFailureType={}",
                    command.name(), LocalClientDiagnostics.elapsedMillis(commandStartedNanos),
                    LocalClientDiagnostics.rootFailureType(exception));
            throw exception;
        }
    }

    /**
     * 客户端可能在原子切换后、服务端 ACK 前退出；启动时先用真实 OpenCode 验证新目录，
     * 失败则恢复 previousDigest 及覆盖前的个人备份。下载阶段中断只终止该次尝试，不触碰当前能力版本。
     */
    static void recoverPublicCapabilityActivation(
            LocalClientPublicCapabilityStore store,
            OpencodeProcessSupervisor supervisor) {
        LocalClientPublicCapabilityStore.State state = store.snapshot();
        if ("PENDING".equals(state.status()) || "DOWNLOADING".equals(state.status())) {
            if (state.pendingCommandId() != null) {
                store.deletePersonalBackup(state.pendingCommandId());
            }
            store.recordStatus("FAILED", "CLIENT_RESTARTED_DURING_UPDATE");
            return;
        }
        if (!"APPLYING".equals(state.status())) {
            return;
        }
        LocalClientPublicCapabilityStore.PersonalBackup personalBackup = state.pendingCommandId() == null
                ? null
                : store.existingPersonalBackup(state.pendingCommandId(), state.previousDigest());
        var health = supervisor.reloadPublicCapabilities(true);
        if (health.success() && health.opencodeHealthy() && supervisor.validatePublicCapabilityCatalog()) {
            store.completeActivation();
            store.deletePersonalBackup(personalBackup);
            return;
        }
        org.slf4j.LoggerFactory.getLogger(LocalClientMain.class).warn(
                "public_capability_activation_validation_failed processStatus={} healthy={}",
                health.processStatus(), health.opencodeHealthy());
        try {
            store.rollback(state.previousDigest(), "OPENCODE_ACTIVATION_FAILED");
            store.restorePersonalBackup(personalBackup);
            var restored = supervisor.reloadPublicCapabilities(true);
            if (!restored.success() || !restored.opencodeHealthy()
                    || !supervisor.validatePublicCapabilityCatalog()) {
                throw new IllegalStateException("公共能力回滚后 OpenCode 未恢复健康: " + restored.message());
            }
            store.deletePersonalBackup(personalBackup);
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
