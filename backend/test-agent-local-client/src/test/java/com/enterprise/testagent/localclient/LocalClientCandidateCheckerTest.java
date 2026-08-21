package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientCandidateCheckerTest {

    private static final String VERSION = "20260820153045";

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldRunCandidateJarWithPreparedJdkAndFailClosedOnNonZeroExit() throws Exception {
        Path release = temporaryDirectory.resolve("releases").resolve(VERSION);
        Path java = release.resolve("jdk/bin/java");
        Path javac = release.resolve("jdk/bin/javac");
        Path jar = release.resolve("test-agent-local-client.jar");
        Files.createDirectories(java.getParent());
        Files.writeString(jar, "candidate");
        Files.writeString(java, "#!/bin/sh\nprintf '%s\\n' \"$@\" > \"$0.args\"\n");
        Files.writeString(javac, "#!/bin/sh\necho 'javac 21.0.9'\n");
        java.toFile().setExecutable(true, true);
        javac.toFile().setExecutable(true, true);

        new LocalClientCandidateChecker().check(java, jar, release, VERSION);

        assertThat(Files.readAllLines(Path.of(java + ".args"))).containsExactlyElementsOf(List.of(
                "-jar",
                jar.toAbsolutePath().normalize().toString(),
                "self-check",
                release.toAbsolutePath().normalize().toString(),
                VERSION));

        Files.writeString(java, "#!/bin/sh\nexit 9\n");
        java.toFile().setExecutable(true, true);
        assertThatThrownBy(() -> new LocalClientCandidateChecker().check(java, jar, release, VERSION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("self-check");
    }

    @Test
    void shouldTimeOutJavacVersionProbeWhenItProducesNoOutput() throws Exception {
        Path release = createReleaseWithJavac("#!/bin/sh\nsleep 5\n");
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> new LocalClientCandidateChecker(Duration.ofMillis(150)).check(
                        release.resolve("jdk/bin/java"),
                        release.resolve("test-agent-local-client.jar"),
                        release,
                        VERSION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("javac timed out");

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void shouldTimeOutJavacVersionProbeWhenItProducesPartialOutputThenHangs() throws Exception {
        Path release = createReleaseWithJavac("#!/bin/sh\nprintf 'javac 21'\nsleep 5\n");
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> new LocalClientCandidateChecker(Duration.ofMillis(150)).check(
                        release.resolve("jdk/bin/java"),
                        release.resolve("test-agent-local-client.jar"),
                        release,
                        VERSION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("javac timed out");

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
    }

    private Path createReleaseWithJavac(String javacScript) throws Exception {
        Path release = temporaryDirectory.resolve("releases").resolve(VERSION);
        Path java = release.resolve("jdk/bin/java");
        Path javac = release.resolve("jdk/bin/javac");
        Files.createDirectories(java.getParent());
        Files.writeString(release.resolve("test-agent-local-client.jar"), "candidate");
        Files.writeString(java, "#!/bin/sh\nexit 0\n");
        Files.writeString(javac, javacScript);
        java.toFile().setExecutable(true, true);
        javac.toFile().setExecutable(true, true);
        return release;
    }
}
