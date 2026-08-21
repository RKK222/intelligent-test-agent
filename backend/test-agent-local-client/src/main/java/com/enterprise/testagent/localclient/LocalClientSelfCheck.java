package com.enterprise.testagent.localclient;

import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** 候选 JAR 在目标 JDK 中执行的最小、无网络自检。 */
final class LocalClientSelfCheck {

    private LocalClientSelfCheck() {
    }

    static void verifyCurrent(Path releaseDirectory, String expectedVersion) throws Exception {
        URI codeSource = LocalClientMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        if (!"file".equalsIgnoreCase(codeSource.getScheme())) {
            throw new IllegalStateException("candidate JAR code source is invalid");
        }
        verify(
                releaseDirectory,
                expectedVersion,
                LocalClientBuildInfo.current(),
                Runtime.version().feature(),
                Path.of(System.getProperty("java.home")),
                Path.of(codeSource));
    }

    /**
     * 校验候选进程确实由发布单元中的 JDK 21 和目标 JAR 启动，并且配套 OpenCode 可执行文件完整。
     */
    static void verify(
            Path releaseDirectory,
            String expectedVersion,
            LocalClientBuildInfo buildInfo,
            int runtimeFeature,
            Path runningJavaHome,
            Path runningJar) throws Exception {
        String version = LocalClientReleaseVersion.parse(expectedVersion).value();
        Path releaseRoot = releaseDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(releaseRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(releaseRoot)
                || !version.equals(releaseRoot.getFileName().toString())) {
            throw new IllegalStateException("candidate release directory is invalid");
        }
        if (runtimeFeature != 21
                || buildInfo == null
                || !buildInfo.managedRelease()
                || !version.equals(buildInfo.clientVersion())) {
            throw new IllegalStateException("candidate Java or build version is incompatible");
        }

        Path expectedJdk = releaseRoot.resolve("jdk");
        Path expectedJava = expectedJdk.resolve("bin/java");
        Path expectedJar = releaseRoot.resolve("test-agent-local-client.jar");
        Path expectedOpencode = releaseRoot.resolve("opencode/bin/opencode");
        requireExecutable(expectedJava, "candidate Java executable is invalid");
        requireRegularFile(expectedJar, "candidate JAR is invalid");
        requireExecutable(expectedOpencode, "candidate OpenCode executable is invalid");

        if (!expectedJdk.toRealPath().equals(runningJavaHome.toAbsolutePath().normalize().toRealPath())
                || !expectedJar.toRealPath().equals(runningJar.toAbsolutePath().normalize().toRealPath())) {
            throw new IllegalStateException("candidate process is not running from the prepared release");
        }
    }

    private static void requireRegularFile(Path path, String message) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireExecutable(Path path, String message) {
        requireRegularFile(path, message);
        if (!Files.isExecutable(path)) {
            throw new IllegalStateException(message);
        }
    }
}
