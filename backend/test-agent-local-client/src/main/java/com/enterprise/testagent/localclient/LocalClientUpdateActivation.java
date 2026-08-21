package com.enterprise.testagent.localclient;

import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 目标 Java 在联网前完成目标 OpenCode 本机启动检查，并与稳定 Shell 交换切换结果。 */
final class LocalClientUpdateActivation {

    static final int LAUNCHER_ACTIVATION_FAILED_EXIT_CODE = 43;
    private static final Duration LAUNCHER_DECISION_TIMEOUT = Duration.ofSeconds(60);

    private final String currentVersion;
    private final LocalClientUpdateMarkerStore markerStore;
    private final OpencodeStarter opencodeStarter;
    private final DecisionWaiter decisionWaiter;
    private final Clock clock;

    LocalClientUpdateActivation(
            String currentVersion,
            LocalClientUpdateMarkerStore markerStore,
            OpencodeStarter opencodeStarter,
            DecisionWaiter decisionWaiter,
            Clock clock) {
        this.currentVersion = LocalClientReleaseVersion.parse(currentVersion).value();
        this.markerStore = Objects.requireNonNull(markerStore);
        this.opencodeStarter = Objects.requireNonNull(opencodeStarter);
        this.decisionWaiter = Objects.requireNonNull(decisionWaiter);
        this.clock = Objects.requireNonNull(clock);
    }

    static LocalClientUpdateActivation production(
            String currentVersion,
            LocalClientUpdateMarkerStore markerStore,
            OpencodeStarter opencodeStarter) {
        return new LocalClientUpdateActivation(
                currentVersion,
                markerStore,
                opencodeStarter,
                pollingWaiter(markerStore),
                Clock.systemUTC());
    }

    /**
     * 返回 0 表示无需激活或启动器已确认成功；43 表示启动器必须回到 pending 中的上一版本。
     * 网络和 WSS 尚未启动，因此这里只有本地 JDK/JAR/OpenCode 故障能触发回滚。
     */
    int activateIfPending() {
        LocalClientUpdateMarkerStore.PendingUpdate pending = markerStore.readPending().orElse(null);
        if (pending == null) {
            return 0;
        }
        LocalClientUpdateMarkerStore.UpdateResult existingDecision = markerStore.readResult().orElse(null);
        if (existingDecision != null) {
            return 0;
        }
        if (!currentVersion.equals(pending.targetVersion())) {
            return LAUNCHER_ACTIVATION_FAILED_EXIT_CODE;
        }

        LocalClientUpdateMarkerStore.ActivationResult activation = markerStore.readActivation().orElse(null);
        if (activation == null) {
            LocalClientPayloads.LifecycleResult started = opencodeStarter.start();
            if (!started.success()
                    || !"RUNNING".equals(started.processStatus())
                    || started.processId() == null
                    || !started.opencodeHealthy()) {
                markerStore.writeActivation(new LocalClientUpdateMarkerStore.ActivationResult(
                        pending,
                        "FAILED",
                        "OPENCODE_START_FAILED",
                        currentVersion,
                        Instant.now(clock)));
                return LAUNCHER_ACTIVATION_FAILED_EXIT_CODE;
            }
            activation = new LocalClientUpdateMarkerStore.ActivationResult(
                    pending,
                    "READY",
                    null,
                    currentVersion,
                    Instant.now(clock));
            markerStore.writeActivation(activation);
        }
        if (!"READY".equals(activation.status())) {
            return LAUNCHER_ACTIVATION_FAILED_EXIT_CODE;
        }
        if (!decisionWaiter.await(LAUNCHER_DECISION_TIMEOUT)) {
            return LAUNCHER_ACTIVATION_FAILED_EXIT_CODE;
        }
        LocalClientUpdateMarkerStore.UpdateResult decision = markerStore.readResult().orElse(null);
        if (decision == null
                || !"SUCCEEDED".equals(decision.status())
                || !currentVersion.equals(decision.actualVersion())) {
            return LAUNCHER_ACTIVATION_FAILED_EXIT_CODE;
        }
        markerStore.clearActivation();
        return 0;
    }

    private static DecisionWaiter pollingWaiter(LocalClientUpdateMarkerStore markerStore) {
        return timeout -> {
            Instant deadline = Instant.now().plus(timeout);
            while (Instant.now().isBefore(deadline)) {
                if (markerStore.readResult().isPresent()) {
                    return true;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return markerStore.readResult().isPresent();
        };
    }

    @FunctionalInterface
    interface OpencodeStarter {
        LocalClientPayloads.LifecycleResult start();
    }

    @FunctionalInterface
    interface DecisionWaiter {
        boolean await(Duration timeout);
    }
}
