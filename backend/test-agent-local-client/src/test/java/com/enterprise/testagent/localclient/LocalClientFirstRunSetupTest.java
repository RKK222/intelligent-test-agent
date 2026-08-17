package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientFirstRunSetupTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesPrivateConfigurationForNativePackage() throws Exception {
        Path configDirectory = temporaryDirectory.resolve("config");
        Path stateDirectory = temporaryDirectory.resolve("state");
        Path opencodeExecutable = temporaryDirectory.resolve("app/opencode");
        Files.createDirectories(opencodeExecutable.getParent());
        Files.writeString(opencodeExecutable, "fixture");
        char[] key = "tack_v1_native-package-test".toCharArray();

        LocalClientFirstRunSetup.persist(
                configDirectory,
                stateDirectory,
                opencodeExecutable,
                new LocalClientFirstRunSetup.SetupValues(
                        "https://platform.example/path",
                        "https://web.example/workbench",
                        key,
                        false),
                true,
                true);

        Properties properties = new Properties();
        try (var input = Files.newInputStream(configDirectory.resolve("client.properties"))) {
            properties.load(input);
        }
        assertThat(properties.getProperty("serverUrl")).isEqualTo("https://platform.example");
        assertThat(properties.getProperty("webUrl")).isEqualTo("https://web.example");
        assertThat(properties.getProperty("opencodeExecutable"))
                .isEqualTo(opencodeExecutable.toAbsolutePath().normalize().toString());
        assertThat(Files.readString(configDirectory.resolve("client.key")).trim())
                .isEqualTo("tack_v1_native-package-test");
        assertThat(key).containsOnly('\0');
        assertThat(Files.getPosixFilePermissions(configDirectory.resolve("client.key")))
                .isEqualTo(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }

    @Test
    void rejectsPlainHttpUnlessDevelopmentPackageExplicitlyAllowsIt() {
        assertThatThrownBy(() -> LocalClientFirstRunSetup.persist(
                temporaryDirectory.resolve("config"),
                temporaryDirectory.resolve("state"),
                temporaryDirectory.resolve("opencode"),
                new LocalClientFirstRunSetup.SetupValues(
                        "http://127.0.0.1:8080",
                        "http://127.0.0.1:3000",
                        "tack_v1_invalid-http".toCharArray(),
                        false),
                true,
                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }
}
