package com.enterprise.testagent.localclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.util.Arrays;

/** 本地 OpenCode 客户端入口；client key 永远不接受命令行参数。 */
public final class LocalClientMain {

    private LocalClientMain() {
    }

    public static void main(String[] args) throws Exception {
        Command command = parseCommand(args);
        if (command == Command.VERSION) {
            System.out.println(versionText(LocalClientBuildInfo.current()));
            return;
        }
        if (command == Command.SELF_CHECK) {
            LocalClientSelfCheck.verifyCurrent(Path.of(args[1]), args[2]);
            System.out.println("本地客户端候选版本自检成功");
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
        LocalWorkspaceRegistry workspaceRegistry = new LocalWorkspaceRegistry(stateStore);
        try (LocalModelRelay modelRelay = new LocalModelRelay(configuration)) {
            OpencodeProcessSupervisor supervisor = new OpencodeProcessSupervisor(
                    configuration, stateStore, modelRelay);
            LocalClientBuildInfo buildInfo = LocalClientBuildInfo.current();
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
            LocalClientFileRpcHandler fileRpcHandler = new LocalClientFileRpcHandler(
                    workspaceRegistry, objectMapper);
            try (LocalClientConnection connection = new LocalClientConnection(
                    configuration,
                    credentials,
                    stateStore,
                    supervisor,
                    modelRelay,
                    fileRpcHandler)) {
                Runtime.getRuntime().addShutdownHook(new Thread(connection::close, "local-client-shutdown"));
                int exitCode = connection.runForever();
                if (exitCode != 0) {
                    System.exit(exitCode);
                }
            }
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
