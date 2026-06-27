package com.icbc.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerIpFileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesSingleLineIpv4AndCreatesParentDirectories() throws Exception {
        Path serverIpFile = tempDir.resolve("nested/.serverip");
        ServerIpFileWriter writer = new ServerIpFileWriter(serverIpFile);

        writer.write("10.8.0.12");

        assertThat(Files.readString(serverIpFile)).isEqualTo("10.8.0.12\n");
    }

    @Test
    void overwritesOldContentWithSingleLineIpv4() throws Exception {
        Path serverIpFile = tempDir.resolve(".serverip");
        Files.writeString(serverIpFile, "old-value\nextra-line\n");
        ServerIpFileWriter writer = new ServerIpFileWriter(serverIpFile);

        writer.write("192.168.1.20");

        assertThat(Files.readString(serverIpFile)).isEqualTo("192.168.1.20\n");
    }
}
