package com.enterprise.testagent.localclient;

import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端更新和回退共用的两阶段状态机。
 *
 * <p>本类只负责可信发布准备、PREPARED/APPLY 协调和稳定启动器 marker；线程调度、WSS envelope
 * generation 以及实际进程退出由连接层负责。</p>
 */
final class LocalClientSelfUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientSelfUpdater.class);
    static final int LAUNCHER_APPLY_EXIT_CODE = 42;

    private final String currentVersion;
    private final String clientInstanceId;
    private final ReleasePreparer releasePreparer;
    private final LocalClientUpdateMarkerStore markerStore;
    private final OpencodeStopper opencodeStopper;
    private final RuntimeControl runtime;
    private final Clock clock;

    private AttemptContext active;

    LocalClientSelfUpdater(
            String currentVersion,
            String clientInstanceId,
            ReleasePreparer releasePreparer,
            LocalClientUpdateMarkerStore markerStore,
            OpencodeStopper opencodeStopper,
            RuntimeControl runtime,
            Clock clock) {
        this.currentVersion = LocalClientReleaseVersion.parse(currentVersion).value();
        if (clientInstanceId == null || !clientInstanceId.matches("lci_[A-Za-z0-9_-]{1,124}")) {
            throw new IllegalArgumentException("local client instance ID is invalid");
        }
        this.clientInstanceId = clientInstanceId;
        this.releasePreparer = Objects.requireNonNull(releasePreparer);
        this.markerStore = Objects.requireNonNull(markerStore);
        this.opencodeStopper = Objects.requireNonNull(opencodeStopper);
        this.runtime = Objects.requireNonNull(runtime);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 下载、验签、解包和候选自检均完成后才发送 PREPARED。重复命令只重发已有结果。 */
    void handleCommand(LocalClientPayloads.UpdateCommand command) {
        validateCommand(command);
        long startedNanos = System.nanoTime();
        LOGGER.info("local_client_update_prepare_started commandId={} generation={} policyRevision={} currentVersion={} targetVersion={} direction={}",
                command.commandId(), command.connectionGeneration(), command.policyRevision(), currentVersion,
                command.targetVersion(), command.direction());
        AttemptContext context;
        synchronized (this) {
            if (active != null) {
                if (!active.command.equals(command)) {
                    throw new IllegalStateException("another local client update command is active");
                }
                if (active.prepared != null && !active.cancelled && !active.applying) {
                    LOGGER.info("local_client_update_prepare_replayed commandId={} targetVersion={}",
                            command.commandId(), command.targetVersion());
                    sendPrepared(active);
                }
                return;
            }
            context = new AttemptContext(command);
            active = context;
        }

        try {
            runtime.sendStatus(status(command, "DOWNLOADING", null));
            LocalClientReleaseDownloader.PreparedRelease prepared = releasePreparer.prepare(
                    command,
                    currentVersion,
                    phase -> {
                        requireActive(context);
                        LOGGER.info("local_client_update_prepare_phase commandId={} targetVersion={} phase={} durationMs={}",
                                command.commandId(), command.targetVersion(), phase,
                                LocalClientDiagnostics.elapsedMillis(startedNanos));
                        runtime.sendStatus(status(command, phase.name(), null));
                    });
            requireActive(context);
            if (!command.targetVersion().equals(prepared.version())) {
                throw new SecurityException("prepared release version does not match update command");
            }
            synchronized (this) {
                requireActive(context);
                context.prepared = prepared;
                context.preparedAt = Instant.now(clock);
            }
            LOGGER.info("local_client_update_prepare_completed commandId={} targetVersion={} releaseDigest={} durationMs={}",
                    command.commandId(), command.targetVersion(), prepared.releaseDigest(),
                    LocalClientDiagnostics.elapsedMillis(startedNanos));
            sendPrepared(context);
        } catch (RuntimeException exception) {
            failPreparation(context, exception);
        } catch (Exception exception) {
            failPreparation(context, exception);
        }
    }

    /** APPLY 必须精确匹配本机已经自检的 release 摘要，随后立即停止接单并交给稳定 Shell。 */
    void handleApply(LocalClientPayloads.UpdateApply apply) {
        AttemptContext context;
        synchronized (this) {
            context = requireMatching(apply);
            if (context.applying) {
                return;
            }
            context.applying = true;
        }

        runtime.quiesce();
        runtime.sendStatus(status(context.command, "APPLYING", null));
        long startedNanos = System.nanoTime();
        LOGGER.info("local_client_update_apply_started commandId={} currentVersion={} targetVersion={} direction={}",
                context.command.commandId(), currentVersion, context.command.targetVersion(),
                context.command.direction());
        try {
            LocalClientPayloads.LifecycleResult stopped = opencodeStopper.stop();
            if (!stopped.success()
                    || !"STOPPED".equals(stopped.processStatus())
                    || stopped.processId() != null
                    || stopped.opencodeHealthy()) {
                failApply(context, "OPENCODE_STOP_FAILED");
                return;
            }
            LOGGER.info("local_client_update_apply_opencode_stopped commandId={} durationMs={}",
                    context.command.commandId(), LocalClientDiagnostics.elapsedMillis(startedNanos));
            markerStore.writePending(
                    context.command,
                    currentVersion,
                    context.prepared.releaseDigest(),
                    context.preparedAt);
            LOGGER.info("local_client_update_apply_handoff_ready commandId={} targetVersion={} durationMs={} exitCode={}",
                    context.command.commandId(), context.command.targetVersion(),
                    LocalClientDiagnostics.elapsedMillis(startedNanos), LAUNCHER_APPLY_EXIT_CODE);
            runtime.requestExit(LAUNCHER_APPLY_EXIT_CODE);
        } catch (RuntimeException exception) {
            LOGGER.warn("local_client_update_apply_failed commandId={} targetVersion={} durationMs={} rootFailureType={}",
                    context.command.commandId(), context.command.targetVersion(),
                    LocalClientDiagnostics.elapsedMillis(startedNanos),
                    LocalClientDiagnostics.rootFailureType(exception));
            failApply(context, "UPDATE_APPLY_FAILED");
        }
    }

    /** 服务端撤销仅能中止尚未 APPLY 的同一命令；已发布的不可变版本目录可以安全复用。 */
    synchronized void handleCancel(LocalClientPayloads.UpdateCancel cancel) {
        validateCancel(cancel);
        if (active == null) {
            return;
        }
        if (!matches(active.command, cancel)) {
            throw new IllegalStateException("update cancellation does not match active command");
        }
        if (active.applying) {
            throw new IllegalStateException("applying local client update cannot be cancelled");
        }
        active.cancelled = true;
        active = null;
        runtime.resume();
        LOGGER.info("local_client_update_cancelled commandId={} targetVersion={} source=server",
                cancel.commandId(), cancel.targetVersion());
    }

    /** 连接 generation 消失后废弃尚未 APPLY 的授权，避免重连后沿用旧 fencing 坐标。 */
    synchronized void handleConnectionLost() {
        if (active != null && !active.applying) {
            LOGGER.info("local_client_update_cancelled commandId={} targetVersion={} source=connection_lost",
                    active.command.commandId(), active.command.targetVersion());
            active.cancelled = true;
            active = null;
            runtime.resume();
        }
    }

    /** 新 Java 注册成功后使用原命令 generation 上报 Shell 留下的终态；必须等待服务端持久化 ACK。 */
    void reportStoredResult() {
        LocalClientUpdateMarkerStore.UpdateResult result = markerStore.readResult().orElse(null);
        if (result == null) {
            return;
        }
        LocalClientUpdateMarkerStore.PendingUpdate pending = result.pending();
        if (!clientInstanceId.equals(pending.clientInstanceId())
                || !currentVersion.equals(result.actualVersion())) {
            throw new IllegalStateException("local client update result does not match running release");
        }
        runtime.sendStatus(new LocalClientPayloads.UpdateStatus(
                pending.commandId(),
                pending.clientInstanceId(),
                pending.connectionGeneration(),
                pending.policyRevision(),
                pending.targetVersion(),
                pending.direction(),
                result.status(),
                result.errorCode(),
                result.observedAt()));
        LOGGER.info("local_client_update_result_reported commandId={} targetVersion={} status={} errorCode={} actualVersion={}",
                pending.commandId(), pending.targetVersion(), result.status(), result.errorCode(),
                result.actualVersion());
    }

    /** 只接受与唯一持久化 result 完全相同的 ACK，错代或重复 ACK 均保持安全幂等。 */
    void handleStatusAck(LocalClientPayloads.UpdateStatusAck ack) {
        Objects.requireNonNull(ack, "update status ack must not be null");
        LocalClientUpdateMarkerStore.UpdateResult result = markerStore.readResult().orElse(null);
        if (result == null) {
            return;
        }
        LocalClientUpdateMarkerStore.PendingUpdate pending = result.pending();
        if (!pending.commandId().equals(ack.commandId())
                || !pending.clientInstanceId().equals(ack.clientInstanceId())
                || pending.connectionGeneration() != ack.connectionGeneration()
                || !result.status().equals(ack.status())) {
            return;
        }
        markerStore.clearPending();
        markerStore.clearResult();
        LOGGER.info("local_client_update_result_acknowledged commandId={} status={}",
                ack.commandId(), ack.status());
    }

    private void sendPrepared(AttemptContext context) {
        runtime.sendStatus(status(context.command, "PREPARING", null));
        runtime.sendPrepared(new LocalClientPayloads.UpdatePrepared(
                context.command.commandId(),
                context.command.clientInstanceId(),
                context.command.connectionGeneration(),
                context.command.policyRevision(),
                context.command.targetVersion(),
                context.command.direction(),
                context.prepared.releaseDigest(),
                context.preparedAt));
    }

    private void failPreparation(AttemptContext context, Exception exception) {
        synchronized (this) {
            if (context.cancelled || active != context) {
                return;
            }
            active = null;
        }
        String errorCode = exception instanceof SecurityException
                ? "ARTIFACT_VERIFICATION_FAILED"
                : exception instanceof IllegalArgumentException
                        ? "RELEASE_INCOMPATIBLE"
                        : Thread.currentThread().isInterrupted()
                                ? "PREPARE_INTERRUPTED"
                                : "PREPARE_FAILED";
        LOGGER.warn("local_client_update_prepare_failed commandId={} targetVersion={} errorCode={} rootFailureType={}",
                context.command.commandId(), context.command.targetVersion(), errorCode,
                LocalClientDiagnostics.rootFailureType(exception));
        runtime.sendStatus(status(context.command, "FAILED", errorCode));
    }

    private void failApply(AttemptContext context, String errorCode) {
        synchronized (this) {
            if (active == context) {
                active = null;
            }
        }
        runtime.sendStatus(status(context.command, "FAILED", errorCode));
        runtime.resume();
        LOGGER.warn("local_client_update_apply_rejected commandId={} targetVersion={} errorCode={}",
                context.command.commandId(), context.command.targetVersion(), errorCode);
    }

    private synchronized AttemptContext requireMatching(LocalClientPayloads.UpdateApply apply) {
        Objects.requireNonNull(apply, "update apply must not be null");
        if (active == null || active.prepared == null || !matches(active.command, apply)
                || !active.prepared.releaseDigest().equals(apply.releaseDigest())) {
            throw new IllegalStateException("update apply does not match prepared release");
        }
        return active;
    }

    private synchronized void requireActive(AttemptContext context) {
        if (active != context || context.cancelled || Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("local client update preparation was cancelled");
        }
    }

    private void validateCommand(LocalClientPayloads.UpdateCommand command) {
        Objects.requireNonNull(command, "update command must not be null");
        if (command.commandId() == null
                || !command.commandId().matches("[A-Za-z0-9_]{1,128}")
                || !clientInstanceId.equals(command.clientInstanceId())
                || command.connectionGeneration() < 1
                || command.policyRevision() < 1) {
            throw new IllegalArgumentException("update command coordinates are invalid");
        }
        String expectedDirection = LocalClientReleaseVersion.parse(currentVersion)
                .directionTo(LocalClientReleaseVersion.parse(command.targetVersion()))
                .name();
        if ("SAME".equals(expectedDirection) || !expectedDirection.equals(command.direction())) {
            throw new IllegalArgumentException("update command direction is invalid");
        }
    }

    private void validateCancel(LocalClientPayloads.UpdateCancel cancel) {
        Objects.requireNonNull(cancel, "update cancel must not be null");
        if (cancel.commandId() == null
                || !cancel.commandId().matches("[A-Za-z0-9_]{1,128}")
                || !clientInstanceId.equals(cancel.clientInstanceId())
                || cancel.connectionGeneration() < 1
                || cancel.policyRevision() < 1) {
            throw new IllegalArgumentException("update cancellation coordinates are invalid");
        }
    }

    private LocalClientPayloads.UpdateStatus status(
            LocalClientPayloads.UpdateCommand command,
            String status,
            String errorCode) {
        return new LocalClientPayloads.UpdateStatus(
                command.commandId(),
                command.clientInstanceId(),
                command.connectionGeneration(),
                command.policyRevision(),
                command.targetVersion(),
                command.direction(),
                status,
                errorCode,
                Instant.now(clock));
    }

    private static boolean matches(
            LocalClientPayloads.UpdateCommand command,
            LocalClientPayloads.UpdateApply apply) {
        return command.commandId().equals(apply.commandId())
                && command.clientInstanceId().equals(apply.clientInstanceId())
                && command.connectionGeneration() == apply.connectionGeneration()
                && command.policyRevision() == apply.policyRevision()
                && command.targetVersion().equals(apply.targetVersion())
                && command.direction().equals(apply.direction());
    }

    private static boolean matches(
            LocalClientPayloads.UpdateCommand command,
            LocalClientPayloads.UpdateCancel cancel) {
        return command.commandId().equals(cancel.commandId())
                && command.clientInstanceId().equals(cancel.clientInstanceId())
                && command.connectionGeneration() == cancel.connectionGeneration()
                && command.policyRevision() == cancel.policyRevision()
                && command.targetVersion().equals(cancel.targetVersion())
                && command.direction().equals(cancel.direction());
    }

    private static final class AttemptContext {
        private final LocalClientPayloads.UpdateCommand command;
        private LocalClientReleaseDownloader.PreparedRelease prepared;
        private Instant preparedAt;
        private boolean cancelled;
        private boolean applying;

        private AttemptContext(LocalClientPayloads.UpdateCommand command) {
            this.command = command;
        }
    }

    @FunctionalInterface
    interface ReleasePreparer {
        LocalClientReleaseDownloader.PreparedRelease prepare(
                LocalClientPayloads.UpdateCommand command,
                String currentVersion,
                LocalClientReleaseDownloader.PreparationListener listener) throws Exception;
    }

    @FunctionalInterface
    interface OpencodeStopper {
        LocalClientPayloads.LifecycleResult stop();
    }

    interface RuntimeControl {
        void sendStatus(LocalClientPayloads.UpdateStatus status);

        void sendPrepared(LocalClientPayloads.UpdatePrepared payload);

        void quiesce();

        void resume();

        void requestExit(int exitCode);
    }
}
