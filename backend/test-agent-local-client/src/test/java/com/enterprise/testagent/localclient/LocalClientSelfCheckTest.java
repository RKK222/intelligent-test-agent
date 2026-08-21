package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientSelfCheckTest {

    private static final String VERSION = "20260820153045";

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldAcceptOnlyMatchingManagedJarRunningOnBundledJdk21() throws Exception {
        Path release = createReleaseLayout();
        Path jar = release.resolve("test-agent-local-client.jar");

        assertThatCode(() -> LocalClientSelfCheck.verify(
                release,
                VERSION,
                LocalClientBuildInfo.resolve(VERSION),
                21,
                release.resolve("jdk"),
                jar))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> LocalClientSelfCheck.verify(
                release,
                "20260820153046",
                LocalClientBuildInfo.resolve(VERSION),
                21,
                release.resolve("jdk"),
                jar))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LocalClientSelfCheck.verify(
                release,
                VERSION,
                LocalClientBuildInfo.resolve(VERSION),
                22,
                release.resolve("jdk"),
                jar))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectMissingOrNonExecutableOpenCode() throws Exception {
        Path release = createReleaseLayout();
        Path opencode = release.resolve("opencode/bin/opencode");
        opencode.toFile().setExecutable(false, false);

        assertThatThrownBy(() -> LocalClientSelfCheck.verify(
                release,
                VERSION,
                LocalClientBuildInfo.resolve(VERSION),
                21,
                release.resolve("jdk"),
                release.resolve("test-agent-local-client.jar")))
                .isInstanceOf(IllegalStateException.class);
    }

    private Path createReleaseLayout() throws Exception {
        Path release = temporaryDirectory.resolve("releases").resolve(VERSION);
        Path java = release.resolve("jdk/bin/java");
        Path opencode = release.resolve("opencode/bin/opencode");
        Files.createDirectories(java.getParent());
        Files.createDirectories(opencode.getParent());
        Files.writeString(java, "java");
        Files.writeString(opencode, "opencode");
        Files.writeString(release.resolve("test-agent-local-client.jar"), "jar");
        java.toFile().setExecutable(true, true);
        opencode.toFile().setExecutable(true, true);
        return release;
    }
}
