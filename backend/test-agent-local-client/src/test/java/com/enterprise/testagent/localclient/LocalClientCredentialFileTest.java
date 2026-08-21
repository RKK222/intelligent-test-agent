package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientCredentialFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldPersistPairedCredentialOutsideReleaseWithPrivatePermissions() throws Exception {
        Path configDirectory = temporaryDirectory.resolve("config");

        LocalClientCredentialFile.write(configDirectory, " UC-001 ", "tack_v1_secret");
        LocalClientCredentialFile.Credentials credentials = LocalClientCredentialFile.read(configDirectory);

        assertThat(credentials.unifiedAuthId()).isEqualTo("UC-001");
        assertThat(credentials.clientKey()).isEqualTo("tack_v1_secret");
        assertThat(credentials.toString()).doesNotContain("tack_v1_secret");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(configDirectory)))
                .isEqualTo("rwx------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(
                configDirectory.resolve("credentials.properties"))))
                .isEqualTo("rw-------");
    }

    @Test
    void shouldRejectBroadCredentialPermissionsAndMalformedValues() throws Exception {
        Path configDirectory = temporaryDirectory.resolve("config");
        LocalClientCredentialFile.write(configDirectory, "UC-001", "tack_v1_secret");
        Path credentialsFile = configDirectory.resolve("credentials.properties");
        Files.setPosixFilePermissions(credentialsFile, PosixFilePermissions.fromString("rw-r--r--"));

        assertThatThrownBy(() -> LocalClientCredentialFile.read(configDirectory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0600");
        assertThatThrownBy(() -> LocalClientCredentialFile.write(
                temporaryDirectory.resolve("bad"), "../bad", "not-a-client-key"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
