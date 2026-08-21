package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientSelfUpdaterTest {

    private static final String INSTANCE_ID = "lci_test";
    private static final String CURRENT_VERSION = "20260820120000";
    private static final String TARGET_VERSION = "20260820153045";
    private static final String RELEASE_DIGEST = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-20T08:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldPrepareSignedReleaseAndRequestTwoPhaseApply() throws Exception {
        RuntimeProbe runtime = new RuntimeProbe();
        LocalClientSelfUpdater updater = updater(
                (command, currentVersion, listener) -> {
                    assertThat(currentVersion).isEqualTo(CURRENT_VERSION);
                    listener.onPhase(LocalClientReleaseDownloader.PreparationPhase.SELF_CHECKING);
                    return preparedRelease();
                },
                stopped(),
                runtime);

        updater.handleCommand(command());

        assertThat(runtime.statuses).extracting(LocalClientPayloads.UpdateStatus::status)
                .containsExactly("DOWNLOADING", "SELF_CHECKING", "PREPARING");
        assertThat(runtime.prepared).singleElement().satisfies(prepared -> {
            assertThat(prepared.commandId()).isEqualTo("lcuc_test");
            assertThat(prepared.releaseDigest()).isEqualTo(RELEASE_DIGEST);
            assertThat(prepared.preparedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void shouldQuiesceStopOpencodePersistMarkerAndExitWithLauncherCode() throws Exception {
        RuntimeProbe runtime = new RuntimeProbe();
        LocalClientUpdateMarkerStore markerStore = markerStore();
        LocalClientSelfUpdater updater = updater(
                (command, currentVersion, listener) -> preparedRelease(),
                stopped(),
                runtime,
                markerStore);
        updater.handleCommand(command());

        updater.handleApply(apply());

        assertThat(runtime.quiesced).isTrue();
        assertThat(runtime.statuses).extracting(LocalClientPayloads.UpdateStatus::status)
                .contains("APPLYING");
        assertThat(runtime.exitCodes).containsExactly(LocalClientSelfUpdater.LAUNCHER_APPLY_EXIT_CODE);
        assertThat(markerStore.readPending()).hasValueSatisfying(pending -> {
            assertThat(pending.currentVersion()).isEqualTo(CURRENT_VERSION);
            assertThat(pending.targetVersion()).isEqualTo(TARGET_VERSION);
            assertThat(pending.releaseDigest()).isEqualTo(RELEASE_DIGEST);
        });
    }

    @Test
    void shouldResumeControlAndReportFailureWhenOpencodeCannotStop() throws Exception {
        RuntimeProbe runtime = new RuntimeProbe();
        LocalClientUpdateMarkerStore markerStore = markerStore();
        LocalClientSelfUpdater updater = updater(
                (command, currentVersion, listener) -> preparedRelease(),
                () -> new LocalClientPayloads.LifecycleResult(
                        false, "FAILED", 123L, NOW, 4096, true, "/opencode", "停止失败"),
                runtime,
                markerStore);
        updater.handleCommand(command());

        updater.handleApply(apply());

        assertThat(runtime.resumed).isTrue();
        assertThat(runtime.exitCodes).isEmpty();
        assertThat(runtime.statuses.getLast().status()).isEqualTo("FAILED");
        assertThat(runtime.statuses.getLast().errorCode()).isEqualTo("OPENCODE_STOP_FAILED");
        assertThat(markerStore.readPending()).isEmpty();
    }

    @Test
    void shouldKeepStoredTerminalResultUntilMatchingServerAck() throws Exception {
        RuntimeProbe runtime = new RuntimeProbe();
        LocalClientUpdateMarkerStore markerStore = markerStore();
        LocalClientUpdateMarkerStore.PendingUpdate pending = markerStore.writePending(
                command(), CURRENT_VERSION, RELEASE_DIGEST, NOW);
        markerStore.writeResult(new LocalClientUpdateMarkerStore.UpdateResult(
                pending, "SUCCEEDED", null, TARGET_VERSION, NOW.plusSeconds(30)));
        LocalClientSelfUpdater updater = new LocalClientSelfUpdater(
                TARGET_VERSION,
                INSTANCE_ID,
                (command, currentVersion, listener) -> preparedRelease(),
                markerStore,
                stopped(),
                runtime,
                Clock.fixed(NOW, ZoneOffset.UTC));

        updater.reportStoredResult();

        assertThat(runtime.statuses).singleElement().satisfies(status -> {
            assertThat(status.status()).isEqualTo("SUCCEEDED");
            assertThat(status.connectionGeneration()).isEqualTo(7);
            assertThat(status.targetVersion()).isEqualTo(TARGET_VERSION);
        });
        assertThat(markerStore.readPending()).contains(pending);
        assertThat(markerStore.readResult()).isPresent();

        updater.handleStatusAck(new LocalClientPayloads.UpdateStatusAck(
                pending.commandId(), pending.clientInstanceId(), 8, "SUCCEEDED"));
        assertThat(markerStore.readPending()).contains(pending);
        assertThat(markerStore.readResult()).isPresent();

        updater.handleStatusAck(new LocalClientPayloads.UpdateStatusAck(
                pending.commandId(), pending.clientInstanceId(), 7, "AUTO_ROLLED_BACK"));
        assertThat(markerStore.readPending()).contains(pending);
        assertThat(markerStore.readResult()).isPresent();

        updater.handleStatusAck(new LocalClientPayloads.UpdateStatusAck(
                pending.commandId(), pending.clientInstanceId(), 7, "SUCCEEDED"));
        assertThat(markerStore.readPending()).isEmpty();
        assertThat(markerStore.readResult()).isEmpty();
    }

    @Test
    void shouldDiscardPreparedAuthorizationWhenConnectionGenerationIsLost() throws Exception {
        RuntimeProbe runtime = new RuntimeProbe();
        LocalClientSelfUpdater updater = updater(
                (command, currentVersion, listener) -> preparedRelease(),
                stopped(),
                runtime);
        updater.handleCommand(command());

        updater.handleConnectionLost();
        updater.handleCommand(new LocalClientPayloads.UpdateCommand(
                "lcuc_reissued", INSTANCE_ID, 8, 12, TARGET_VERSION, "UPDATE"));

        assertThat(runtime.resumed).isTrue();
        assertThat(runtime.prepared).extracting(LocalClientPayloads.UpdatePrepared::commandId)
                .containsExactly("lcuc_test", "lcuc_reissued");
    }

    private LocalClientSelfUpdater updater(
            LocalClientSelfUpdater.ReleasePreparer preparer,
            LocalClientSelfUpdater.OpencodeStopper stopper,
            RuntimeProbe runtime) throws Exception {
        return updater(preparer, stopper, runtime, markerStore());
    }

    private LocalClientSelfUpdater updater(
            LocalClientSelfUpdater.ReleasePreparer preparer,
            LocalClientSelfUpdater.OpencodeStopper stopper,
            RuntimeProbe runtime,
            LocalClientUpdateMarkerStore markerStore) {
        return new LocalClientSelfUpdater(
                CURRENT_VERSION,
                INSTANCE_ID,
                preparer,
                markerStore,
                stopper,
                runtime,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private LocalClientUpdateMarkerStore markerStore() throws Exception {
        return new LocalClientUpdateMarkerStore(temporaryDirectory.resolve("state-" + System.nanoTime()));
    }

    private LocalClientReleaseDownloader.PreparedRelease preparedRelease() throws Exception {
        Path releaseDirectory = temporaryDirectory.resolve("releases").resolve(TARGET_VERSION);
        java.nio.file.Files.createDirectories(releaseDirectory);
        return new LocalClientReleaseDownloader.PreparedRelease(
                TARGET_VERSION, RELEASE_DIGEST, releaseDirectory);
    }

    private static LocalClientPayloads.UpdateCommand command() {
        return new LocalClientPayloads.UpdateCommand(
                "lcuc_test", INSTANCE_ID, 7, 11, TARGET_VERSION, "UPDATE");
    }

    private static LocalClientPayloads.UpdateApply apply() {
        return new LocalClientPayloads.UpdateApply(
                "lcuc_test", INSTANCE_ID, 7, 11, TARGET_VERSION, "UPDATE", RELEASE_DIGEST);
    }

    private static LocalClientSelfUpdater.OpencodeStopper stopped() {
        return () -> new LocalClientPayloads.LifecycleResult(
                true, "STOPPED", null, null, 4096, false, "/opencode", "已停止");
    }

    private static final class RuntimeProbe implements LocalClientSelfUpdater.RuntimeControl {
        private final List<LocalClientPayloads.UpdateStatus> statuses = new ArrayList<>();
        private final List<LocalClientPayloads.UpdatePrepared> prepared = new ArrayList<>();
        private final List<Integer> exitCodes = new ArrayList<>();
        private boolean quiesced;
        private boolean resumed;

        @Override
        public void sendStatus(LocalClientPayloads.UpdateStatus status) {
            statuses.add(status);
        }

        @Override
        public void sendPrepared(LocalClientPayloads.UpdatePrepared payload) {
            prepared.add(payload);
        }

        @Override
        public void quiesce() {
            quiesced = true;
        }

        @Override
        public void resume() {
            resumed = true;
        }

        @Override
        public void requestExit(int exitCode) {
            exitCodes.add(exitCode);
        }
    }
}
