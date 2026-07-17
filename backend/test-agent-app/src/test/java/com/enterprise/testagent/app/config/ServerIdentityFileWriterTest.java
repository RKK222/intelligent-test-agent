package com.enterprise.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerIdentityFileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesSingleLineIdentityAndHostAndCreatesParentDirectories() throws Exception {
        Path serverIdFile = tempDir.resolve("nested/.serverid");
        Path serverHostFile = tempDir.resolve("nested/.serverhost");
        ServerIdentityFileWriter writer = new ServerIdentityFileWriter(serverIdFile, serverHostFile);

        writer.write("linux-prod-a", "10.8.0.12");

        assertThat(Files.readString(serverIdFile)).isEqualTo("linux-prod-a\n");
        assertThat(Files.readString(serverHostFile)).isEqualTo("10.8.0.12\n");
    }

    @Test
    void overwritesOldContentWithSingleLineValues() throws Exception {
        Path serverIdFile = tempDir.resolve(".serverid");
        Path serverHostFile = tempDir.resolve(".serverhost");
        Files.writeString(serverIdFile, "old-id\nextra-line\n");
        Files.writeString(serverHostFile, "old-host\nextra-line\n");
        ServerIdentityFileWriter writer = new ServerIdentityFileWriter(serverIdFile, serverHostFile);

        writer.write("server-a_01", "backend.internal");

        assertThat(Files.readString(serverIdFile)).isEqualTo("server-a_01\n");
        assertThat(Files.readString(serverHostFile)).isEqualTo("backend.internal\n");
    }
}
