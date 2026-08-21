package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientUpdateMarkerStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldPersistPendingAndResultMarkersOutsideReleaseDirectoryWithPrivatePermissions() throws Exception {
        LocalClientUpdateMarkerStore store = new LocalClientUpdateMarkerStore(temporaryDirectory.resolve("state"));
        LocalClientPayloads.UpdateCommand command = new LocalClientPayloads.UpdateCommand(
                "lcuc_test",
                "lci_test",
                7,
                11,
                "20260820153045",
                "UPDATE");
        LocalClientUpdateMarkerStore.PendingUpdate pending = store.writePending(
                command,
                "20260820120000",
                "a".repeat(64),
                Instant.parse("2026-08-20T07:31:00Z"));

        assertThat(store.readPending()).contains(pending);
        assertThat(store.pendingPath().getParent()).isEqualTo(temporaryDirectory.resolve("state"));
        if (Files.getFileStore(store.pendingPath()).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(store.pendingPath()))
                    .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        }

        LocalClientUpdateMarkerStore.UpdateResult result = new LocalClientUpdateMarkerStore.UpdateResult(
                pending,
                "SUCCEEDED",
                null,
                "20260820153045",
                Instant.parse("2026-08-20T07:32:00Z"));
        store.writeResult(result);
        assertThat(store.readResult()).contains(result);

        LocalClientUpdateMarkerStore.ActivationResult activation =
                new LocalClientUpdateMarkerStore.ActivationResult(
                        pending,
                        "READY",
                        null,
                        "20260820153045",
                        Instant.parse("2026-08-20T07:31:30Z"));
        store.writeActivation(activation);
        assertThat(store.readActivation()).contains(activation);

        store.clearPending();
        store.clearResult();
        store.clearActivation();
        assertThat(store.readPending()).isEmpty();
        assertThat(store.readResult()).isEmpty();
        assertThat(store.readActivation()).isEmpty();
    }
}
