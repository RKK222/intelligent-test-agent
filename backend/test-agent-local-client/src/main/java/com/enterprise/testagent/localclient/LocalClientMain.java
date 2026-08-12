package com.enterprise.testagent.localclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
            try (LocalClientConnection connection = new LocalClientConnection(
                    configuration,
                    clientKey,
                    stateStore,
                    supervisor,
                    modelRelay,
                    fileRpcHandler)) {
                Runtime.getRuntime().addShutdownHook(new Thread(connection::close, "local-client-shutdown"));
                connection.runForever();
            }
        }
    }
}
