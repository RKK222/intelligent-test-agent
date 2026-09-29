package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Path;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证本地模型与 Trace relay 权限隔离，且注入共享插件时不覆盖用户配置。 */
class OpencodeObservabilityInjectionTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void keepsExistingPluginsDeduplicatesObservabilityPluginAndUsesAnIndependentToken() throws Exception {
        LocalClientConfiguration configuration = new LocalClientConfiguration(
                URI.create("https://127.0.0.1:65534"),
                URI.create("https://127.0.0.1:65534"),
                "observability-injection-test",
                temporaryDirectory.resolve("opencode"),
                temporaryDirectory.resolve("config"),
                temporaryDirectory.resolve("data"),
                4096,
                4097,
                false);
        LocalClientStateStore stateStore = new LocalClientStateStore(temporaryDirectory.resolve("state"));
        try (LocalModelRelay modelRelay = new LocalModelRelay(configuration);
                LocalObservabilityRelay observabilityRelay = new LocalObservabilityRelay(
                        temporaryDirectory.resolve("spool"), 1024 * 1024)) {
            OpencodeProcessSupervisor supervisor = new OpencodeProcessSupervisor(
                    configuration, stateStore, modelRelay, observabilityRelay);
            String pluginUri = "file:///opt/test-agent/plugins/test-agent-observability/";
            String once = supervisor.withObservabilityPlugin(
                    "{\"plugin\":[\"file:///user/plugin.mjs\"],\"model\":\"keep-me\"}", pluginUri);
            String twice = supervisor.withObservabilityPlugin(once, pluginUri);
            var root = new ObjectMapper().readTree(twice);

            assertThat(root.path("model").asText()).isEqualTo("keep-me");
            assertThat(StreamSupport.stream(root.path("plugins").spliterator(), false)
                    .map(node -> node.path("package").asText()).toList())
                    .containsExactly("file:///user/plugin.mjs", pluginUri);
            assertThat(root.has("plugin")).isFalse();
            assertThat(observabilityRelay.localToken())
                    .isNotBlank()
                    .isNotEqualTo(modelRelay.localToken());
            assertThat(observabilityRelay.baseUrl()).isNotEqualTo(modelRelay.baseUrl());
        }
    }
}
