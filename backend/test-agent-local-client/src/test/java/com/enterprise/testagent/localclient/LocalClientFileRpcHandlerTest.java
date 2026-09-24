package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientFileRpcHandlerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldPersistStableIdentityAndExecuteFullWorkspaceCrud() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("workspace"));
        LocalClientStateStore stateStore = new LocalClientStateStore(temporaryDirectory.resolve("state"));
        String instanceId = stateStore.read().clientInstanceId();
        assertThat(instanceId).startsWith("lci_");
        assertThat(new LocalClientStateStore(temporaryDirectory.resolve("state")).read().clientInstanceId())
                .isEqualTo(instanceId);
        assertThat(Files.getPosixFilePermissions(temporaryDirectory.resolve("state/state.json")))
                .isEqualTo(PosixFilePermissions.fromString("rw-------"));

        ObjectMapper objectMapper = objectMapper();
        LocalWorkspaceRegistry registry = new LocalWorkspaceRegistry(stateStore);
        LocalWorkspaceRegistry.Registration registration = registry.register("wrk_local", root.toString());
        LocalClientFileRpcHandler handler = new LocalClientFileRpcHandler(registry, objectMapper);

        handler.handle(request(objectMapper, registration, "workspace.mkdir", params(objectMapper, "path", "src")));
        ObjectNode write = params(objectMapper, "path", "src/a.txt");
        write.put("content", "hello");
        handler.handle(request(objectMapper, registration, "workspace.write", write));
        assertThat(Files.readString(root.resolve("src/a.txt"))).isEqualTo("hello");

        ObjectNode copy = params(objectMapper, "sourcePath", "src/a.txt");
        copy.put("targetPath", "src/b.txt");
        handler.handle(request(objectMapper, registration, "workspace.copy", copy));
        ObjectNode move = params(objectMapper, "sourcePath", "src/b.txt");
        move.put("targetPath", "src/moved.txt");
        handler.handle(request(objectMapper, registration, "workspace.move", move));
        ObjectNode rename = params(objectMapper, "path", "src/moved.txt");
        rename.put("name", "renamed.txt");
        handler.handle(request(objectMapper, registration, "workspace.rename", rename));
        assertThat(root.resolve("src/renamed.txt")).exists();

        byte[] binary = new byte[] {0, 1, 2, 3, 4};
        ObjectNode begin = params(objectMapper, "path", "src/data.bin");
        begin.put("expectedBytes", binary.length);
        String uploadId = handler.handle(request(
                        objectMapper, registration, "workspace.upload.begin", begin))
                .path("uploadId").asText();
        ObjectNode chunk = params(objectMapper, "uploadId", uploadId);
        chunk.put("index", 0);
        chunk.put("contentBase64", Base64.getEncoder().encodeToString(binary));
        handler.handle(request(objectMapper, registration, "workspace.upload.chunk", chunk));
        handler.handle(request(objectMapper, registration, "workspace.upload.complete",
                params(objectMapper, "uploadId", uploadId)));
        assertThat(Files.readAllBytes(root.resolve("src/data.bin"))).containsExactly(binary);

        assertThat(handler.handle(request(objectMapper, registration, "workspace.search",
                params(objectMapper, "query", "renamed"))).toString()).contains("renamed.txt");
        assertThat(handler.handle(request(objectMapper, registration, "workspace.read",
                params(objectMapper, "path", "src/a.txt"))).toString()).contains("hello");

        handler.handle(request(objectMapper, registration, "workspace.delete",
                params(objectMapper, "path", "src/renamed.txt")));
        assertThat(root.resolve("src/renamed.txt")).doesNotExist();
        registry.unregister("wrk_local");
        assertThatThrownBy(() -> registry.requireRoot("wrk_local", registration.rootDigest()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectWrongDigestTraversalAndSymbolicLinkRoot() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("workspace"));
        Path outside = Files.writeString(
                temporaryDirectory.resolve("outside.txt"), "secret", StandardCharsets.UTF_8);
        LocalClientStateStore stateStore = new LocalClientStateStore(temporaryDirectory.resolve("state"));
        LocalWorkspaceRegistry registry = new LocalWorkspaceRegistry(stateStore);
        LocalWorkspaceRegistry.Registration registration = registry.register("wrk_local", root.toString());
        ObjectMapper objectMapper = objectMapper();
        LocalClientFileRpcHandler handler = new LocalClientFileRpcHandler(registry, objectMapper);

        assertThatThrownBy(() -> registry.requireRoot("wrk_local", "wrong"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("digest");
        assertThatThrownBy(() -> handler.handle(request(
                        objectMapper, registration, "workspace.read",
                        params(objectMapper, "path", "../outside.txt"))))
                .isInstanceOf(PlatformException.class);
        assertThat(outside).hasContent("secret");

        Path link = temporaryDirectory.resolve("workspace-link");
        Files.createSymbolicLink(link, root);
        assertThatThrownBy(() -> registry.validate(link.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbolic link");
    }

    @Test
    void shouldBoundActiveUploadsAndReleaseSlotsWhenConnectionCloses() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("workspace"));
        LocalClientStateStore stateStore = new LocalClientStateStore(temporaryDirectory.resolve("state"));
        LocalWorkspaceRegistry registry = new LocalWorkspaceRegistry(stateStore);
        LocalWorkspaceRegistry.Registration registration = registry.register("wrk_local", root.toString());
        ObjectMapper objectMapper = objectMapper();
        LocalClientFileRpcHandler handler = new LocalClientFileRpcHandler(registry, objectMapper);

        for (int index = 0; index < 64; index++) {
            ObjectNode begin = params(objectMapper, "path", "upload-" + index + ".bin");
            begin.put("expectedBytes", 0);
            handler.handle(request(objectMapper, registration, "workspace.upload.begin", begin));
        }
        ObjectNode overflow = params(objectMapper, "path", "overflow.bin");
        overflow.put("expectedBytes", 0);
        assertThatThrownBy(() -> handler.handle(request(
                        objectMapper, registration, "workspace.upload.begin", overflow)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too many active");

        handler.abortAll();
        assertThat(handler.handle(request(objectMapper, registration, "workspace.upload.begin", overflow))
                .path("uploadId").asText()).startsWith("lup_");
        handler.abortAll();
    }

    @Test
    void shouldExposePersonalDirectoryInStatusWithoutCreatingDraft() throws Exception {
        Path stateDirectory = temporaryDirectory.resolve("state");
        LocalClientStateStore stateStore = new LocalClientStateStore(stateDirectory);
        LocalWorkspaceRegistry registry = new LocalWorkspaceRegistry(stateStore);
        LocalWorkspaceRegistry.Registration registration = registry.register(
                "wrk_local", Files.createDirectory(temporaryDirectory.resolve("workspace")).toString());
        ObjectMapper mapper = objectMapper();
        LocalClientPublicCapabilityStore store = new LocalClientPublicCapabilityStore(stateDirectory);
        LocalClientFileRpcHandler handler = new LocalClientFileRpcHandler(
                registry, mapper, new LocalGitAccessChecker(), store);

        var status = handler.handle(request(mapper, registration, "agent-config.status", mapper.createObjectNode()));
        assertThat(status.path("supported").asBoolean()).isTrue();
        assertThat(status.path("personalized").asBoolean()).isFalse();
        assertThat(status.path("personalDirectory").asText())
                .isEqualTo(stateDirectory.resolve("public-capabilities/personal").toAbsolutePath().toString());
        assertThat(stateDirectory.resolve("public-capabilities/personal")).doesNotExist();
    }

    private static LocalClientPayloads.FileRequest request(
            ObjectMapper objectMapper,
            LocalWorkspaceRegistry.Registration registration,
            String operation,
            ObjectNode parameters) {
        return new LocalClientPayloads.FileRequest(
                "wrk_local", registration.rootDigest(), operation, objectMapper.valueToTree(parameters));
    }

    private static ObjectNode params(ObjectMapper objectMapper, String name, String value) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(name, value);
        return node;
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
