package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalBrowserSettingsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesOnlyAbsolute360DesktopExecutableWithoutShellExpansion() throws Exception {
        Path desktop = temporaryDirectory.resolve("360.desktop");
        Files.writeString(desktop, "[Desktop Entry]\nName=360安全浏览器\nExec=\"/opt/360 browser/360browser\" %U\n",
                StandardCharsets.UTF_8);

        assertThat(LocalBrowserSettings.parseDesktopExecutable(desktop))
                .contains(Path.of("/opt/360 browser/360browser"));

        Files.writeString(desktop, "[Desktop Entry]\nName=360安全浏览器\nExec=$HOME/360browser %U\n",
                StandardCharsets.UTF_8);
        assertThat(LocalBrowserSettings.parseDesktopExecutable(desktop)).isEmpty();
    }

    @Test
    void prefersEnterprise360StableLauncherBeforeFallbackDiscovery() {
        assertThat(LocalBrowserSettings.KYLIN_CANDIDATES.getFirst())
                .isEqualTo(Path.of("/usr/bin/browser360ent-cn-stable"));
    }

    @Test
    void persistsCanonicalExecutableAndRejectsNonExecutableFile() throws Exception {
        Path executable = temporaryDirectory.resolve("360browser");
        Files.writeString(executable, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        LocalBrowserSettings settings = new LocalBrowserSettings(
                temporaryDirectory.resolve("config"), temporaryDirectory.resolve("state"));

        settings.saveExecutable(executable);

        assertThat(settings.configuredExecutable()).isEqualTo(executable.toRealPath());
        assertThat(settings.resolveExecutable()).isEqualTo(executable.toRealPath());
        assertThat(settings.profileDirectory()).isEqualTo(
                temporaryDirectory.resolve("state/browser/profile").toAbsolutePath().normalize());
        assertThat(settings.prepareProfileDirectory()).isEqualTo(settings.profileDirectory());
        assertThat(Files.getPosixFilePermissions(settings.profileDirectory()))
                .containsExactlyInAnyOrderElementsOf(PosixFilePermissions.fromString("rwx------"));

        Path ordinary = temporaryDirectory.resolve("ordinary-file");
        Files.writeString(ordinary, "not executable", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> settings.saveExecutable(ordinary))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不可执行");
    }

    @Test
    void extractsChromiumMajorFromBrowserOrUserAgent() {
        assertThat(LocalBrowserSupervisor.chromiumMajor("Chrome/132.0.1", "")).isEqualTo(132);
        assertThat(LocalBrowserSupervisor.chromiumMajor("360Browser/15", "Mozilla Chrome/122.0.0.0"))
                .isEqualTo(122);
        assertThat(LocalBrowserSupervisor.chromiumMajor("unknown", "unknown")).isEqualTo(-1);
    }

    @Test
    void enablesBrowserOnlyForLinuxArm64Client() {
        assertThat(LocalBrowserSettings.supportedPlatform(LocalClientPlatform.detect("Kylin Linux", "aarch64")))
                .isTrue();
        assertThat(LocalBrowserSettings.supportedPlatform(LocalClientPlatform.detect("Linux", "x86_64")))
                .isFalse();
        assertThat(LocalBrowserSettings.supportedPlatform(LocalClientPlatform.detect("Windows 10", "amd64")))
                .isFalse();
    }
}
