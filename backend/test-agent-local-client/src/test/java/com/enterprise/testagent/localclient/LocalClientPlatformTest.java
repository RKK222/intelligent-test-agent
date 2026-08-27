package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalClientPlatformTest {

    @Test
    void shouldNormalizeWindowsTenX64AndExecutablePaths() {
        LocalClientPlatform platform = LocalClientPlatform.detect("Windows 10", "amd64");
        Path release = Path.of("release");

        assertThat(platform.platform()).isEqualTo("windows");
        assertThat(platform.architecture()).isEqualTo("x64");
        assertThat(platform.javaExecutable(release)).isEqualTo(release.resolve("jdk/bin/java.exe"));
        assertThat(platform.javacExecutable(release)).isEqualTo(release.resolve("jdk/bin/javac.exe"));
        assertThat(platform.managedJavaExecutable(release)).isEqualTo(release.resolve("jdk/bin/javaw.exe"));
        assertThat(platform.opencodeExecutable(release)).isEqualTo(release.resolve("opencode/bin/opencode.exe"));
        assertThat(platform.displayName()).isEqualTo("windows-x64");
    }

    @Test
    void shouldKeepKylinArm64ProtocolValues() {
        LocalClientPlatform platform = LocalClientPlatform.detect("Linux", "aarch64");

        assertThat(platform.platform()).isEqualTo("linux");
        assertThat(platform.architecture()).isEqualTo("arm64");
        assertThat(platform.javaExecutable(Path.of("release")))
                .isEqualTo(Path.of("release/jdk/bin/java"));
    }
}
