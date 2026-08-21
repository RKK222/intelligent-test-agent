package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientUpdateActivationTest {

    private static final String CURRENT_VERSION = "20260820120000";
    private static final String TARGET_VERSION = "20260820153045";
    private static final Instant NOW = Instant.parse("2026-08-20T08:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldStartTargetOpencodeAndWaitForLauncherSuccessDecision() throws Exception {
        LocalClientUpdateMarkerStore store = storeWithPending();
        LocalClientUpdateActivation activation = new LocalClientUpdateActivation(
                TARGET_VERSION,
                store,
                () -> lifecycle(true, "RUNNING", true),
                timeout -> {
                    assertThat(store.readActivation()).hasValueSatisfying(result ->
                            assertThat(result.status()).isEqualTo("READY"));
                    LocalClientUpdateMarkerStore.PendingUpdate pending = store.readPending().orElseThrow();
                    store.writeResult(new LocalClientUpdateMarkerStore.UpdateResult(
                            pending, "SUCCEEDED", null, TARGET_VERSION, NOW.plusSeconds(1)));
                    return true;
                },
                Clock.fixed(NOW, ZoneOffset.UTC));

        int exitCode = activation.activateIfPending();

        assertThat(exitCode).isZero();
        assertThat(store.readActivation()).isEmpty();
        assertThat(store.readResult()).hasValueSatisfying(result ->
                assertThat(result.status()).isEqualTo("SUCCEEDED"));
    }

    @Test
    void shouldExposeLocalStartupFailureForLauncherRollback() throws Exception {
        LocalClientUpdateMarkerStore store = storeWithPending();
        LocalClientUpdateActivation activation = new LocalClientUpdateActivation(
                TARGET_VERSION,
                store,
                () -> lifecycle(false, "FAILED", false),
                timeout -> false,
                Clock.fixed(NOW, ZoneOffset.UTC));

        int exitCode = activation.activateIfPending();

        assertThat(exitCode).isEqualTo(LocalClientUpdateActivation.LAUNCHER_ACTIVATION_FAILED_EXIT_CODE);
        assertThat(store.readActivation()).hasValueSatisfying(result -> {
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.errorCode()).isEqualTo("OPENCODE_START_FAILED");
        });
    }

    @Test
    void shouldNotStartOpencodeWithoutPendingReleaseSwitch() throws Exception {
        LocalClientUpdateMarkerStore store = new LocalClientUpdateMarkerStore(temporaryDirectory.resolve("empty"));
        AtomicInteger starts = new AtomicInteger();
        LocalClientUpdateActivation activation = new LocalClientUpdateActivation(
                TARGET_VERSION,
                store,
                () -> {
                    starts.incrementAndGet();
                    return lifecycle(true, "RUNNING", true);
                },
                timeout -> false,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(activation.activateIfPending()).isZero();
        assertThat(starts).hasValue(0);
    }

    private LocalClientUpdateMarkerStore storeWithPending() throws Exception {
        LocalClientUpdateMarkerStore store = new LocalClientUpdateMarkerStore(
                temporaryDirectory.resolve("state-" + System.nanoTime()));
        store.writePending(
                new LocalClientPayloads.UpdateCommand(
                        "lcuc_test", "lci_test", 7, 11, TARGET_VERSION, "UPDATE"),
                CURRENT_VERSION,
                "a".repeat(64),
                NOW.minusSeconds(5));
        return store;
    }

    private static LocalClientPayloads.LifecycleResult lifecycle(
            boolean success,
            String status,
            boolean healthy) {
        return new LocalClientPayloads.LifecycleResult(
                success,
                status,
                healthy ? 123L : null,
                healthy ? NOW.minusSeconds(1) : null,
                4096,
                healthy,
                "/opencode",
                success ? "启动成功" : "启动失败");
    }
}
