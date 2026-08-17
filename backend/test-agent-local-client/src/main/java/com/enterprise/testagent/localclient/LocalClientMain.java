package com.enterprise.testagent.localclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.util.Arrays;

/** 本地 OpenCode 客户端入口；client key 永远不接受命令行参数。 */
public final class LocalClientMain {

    private LocalClientMain() {
    }

    public static void main(String[] args) throws Exception {
        if (Arrays.stream(args).anyMatch(argument -> argument.toLowerCase().contains("key"))) {
            throw new IllegalArgumentException("client key must be stored in the private client.key file");
        }
        if (args.length == 1 && "--version".equals(args[0])) {
            System.out.println("test-agent-local-client 0.1.0");
            return;
        }
        if (args.length != 0) {
            throw new IllegalArgumentException("unsupported command line arguments");
        }

        // macOS 菜单栏客户端不应额外占用 Dock；日志属性必须在日志框架初始化前设置。
        System.setProperty("apple.awt.UIElement", "true");
        Files.createDirectories(LocalClientPaths.logsDirectory());
        System.setProperty("testagent.localclient.logDir", LocalClientPaths.logsDirectory().toString());
        LocalClientConfiguration configuration = LocalClientConfiguration.load();
        String clientKey = LocalClientCredentialFile.read();
        LocalClientStateStore stateStore = new LocalClientStateStore();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        LocalWorkspaceRegistry workspaceRegistry = new LocalWorkspaceRegistry(stateStore);
        try (LocalModelRelay modelRelay = new LocalModelRelay(configuration)) {
            OpencodeProcessSupervisor supervisor = new OpencodeProcessSupervisor(
                    configuration, stateStore, modelRelay);
            LocalClientFileRpcHandler fileRpcHandler = new LocalClientFileRpcHandler(
                    workspaceRegistry, objectMapper);
            LocalClientConnection connection = new LocalClientConnection(
                    configuration,
                    clientKey,
                    stateStore,
                    supervisor,
                    modelRelay,
                    fileRpcHandler);
            LocalClientTray tray = LocalClientTray.install(configuration, connection);
            try (connection; tray) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    tray.close();
                    connection.close();
                }, "local-client-shutdown"));
                connection.runForever();
            }
        }
    }
}
