package com.enterprise.testagent.domain.appsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppSourceDomainTest {

    private static final Instant NOW = Instant.parse("2026-07-28T02:00:00Z");

    @Test
    void materializationRetentionAcceptsOneToSeventyTwoHoursAndDefaultsToFortyEight() {
        assertThat(new AppSourceRetention(1).expiresAt(NOW)).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(new AppSourceRetention(168).expiresAt(NOW)).isEqualTo(NOW.plus(Duration.ofHours(168)));
        assertThat(AppSourceRetention.defaultRetention().hours()).isEqualTo(48);
        assertThat(AppSourceRetention.defaultRetention().expiresAt(NOW)).isEqualTo(NOW.plus(Duration.ofHours(48)));

        assertThatThrownBy(() -> new AppSourceRetention(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppSourceRetention(169)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selectedPathsAreNormalizedAndRejectAbsoluteOrTraversalValues() {
        assertThat(new AppSourceSelectedPath(" src\\main ", AppSourcePathType.DIRECTORY).path())
                .isEqualTo("src/main");
        assertThatThrownBy(() -> new AppSourceSelectedPath("../secret", AppSourcePathType.FILE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppSourceSelectedPath("/etc/passwd", AppSourcePathType.FILE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppSourceSelectedPath("D:/secret", AppSourcePathType.DIRECTORY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotFreezesSelectionAndRequiresPersonalOwner() {
        List<AppSourceSelectedPath> selectedPaths = new java.util.ArrayList<>(List.of(
                new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)));
        AppSourceSnapshot snapshot = snapshot(AppSourcePurpose.PERSONAL, new UserId("usr_owner"), selectedPaths);
        selectedPaths.clear();

        assertThat(snapshot.selectedPaths()).containsExactly(
                new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY));
        assertThatThrownBy(() -> snapshot(AppSourcePurpose.PERSONAL, null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void snapshotExpiryMustBeWholeHoursWithinRetentionWindow() {
        assertThat(snapshotWithExpiry(NOW.plus(Duration.ofHours(1))).expiresAt())
                .isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(snapshotWithExpiry(NOW.plus(Duration.ofHours(168))).expiresAt())
                .isEqualTo(NOW.plus(Duration.ofHours(168)));

        assertThatThrownBy(() -> snapshotWithExpiry(NOW.plus(Duration.ofHours(169))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> snapshotWithExpiry(NOW.plus(Duration.ofMinutes(90))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statesAllowOnlyForwardBusinessTransitions() {
        assertThat(AppSourceSnapshotStatus.PENDING.canTransitionTo(AppSourceSnapshotStatus.ACTIVE)).isTrue();
        assertThat(AppSourceSnapshotStatus.ACTIVE.canTransitionTo(AppSourceSnapshotStatus.EXPIRED)).isTrue();
        assertThat(AppSourceSnapshotStatus.EXPIRED.canTransitionTo(AppSourceSnapshotStatus.CLEANED)).isTrue();
        assertThat(AppSourceSnapshotStatus.CLEANED.canTransitionTo(AppSourceSnapshotStatus.ACTIVE)).isFalse();

        assertThat(AppSourceReplicaStatus.PENDING.canTransitionTo(AppSourceReplicaStatus.RUNNING)).isTrue();
        assertThat(AppSourceReplicaStatus.FAILED.canTransitionTo(AppSourceReplicaStatus.RUNNING)).isTrue();
        assertThat(AppSourceReplicaStatus.STALE.canTransitionTo(AppSourceReplicaStatus.RUNNING)).isTrue();
        assertThat(AppSourceReplicaStatus.RUNNING.canTransitionTo(AppSourceReplicaStatus.READY)).isTrue();
        assertThat(AppSourceReplicaStatus.READY.canTransitionTo(AppSourceReplicaStatus.STALE)).isTrue();
        assertThat(AppSourceReplicaStatus.CLEANED.canTransitionTo(AppSourceReplicaStatus.PENDING)).isFalse();

        assertThat(AppSourceOperationStatus.PENDING.canTransitionTo(AppSourceOperationStatus.RUNNING)).isTrue();
        assertThat(AppSourceOperationStatus.RUNNING.canTransitionTo(AppSourceOperationStatus.PARTIAL_FAILED)).isTrue();
        assertThat(AppSourceOperationStatus.SUCCEEDED.canTransitionTo(AppSourceOperationStatus.RUNNING)).isFalse();

        assertThat(AppSourceCleanupStatus.RETRY_WAIT.canTransitionTo(AppSourceCleanupStatus.RUNNING)).isTrue();
        assertThat(AppSourceCleanupStatus.CLEANED.canTransitionTo(AppSourceCleanupStatus.RUNNING)).isFalse();
        assertThat(AppSourceStepStatus.PENDING.canTransitionTo(AppSourceStepStatus.SKIPPED)).isTrue();
        assertThat(AppSourceStepStatus.SUCCEEDED.canTransitionTo(AppSourceStepStatus.RUNNING)).isFalse();
    }

    @Test
    void slotAndReplicaExposeOptimisticAndGenerationFencing() {
        AppSourceRepositorySlot slot = new AppSourceRepositorySlot(
                new CodeRepositoryId("repo_source"), 3L, 4L, 5L, "op_latest", 8L, NOW, NOW);
        AppSourceReplica replica = new AppSourceReplica(
                new CodeRepositoryId("repo_source"), 4L, new LinuxServerId("server-a"), null,
                AppSourceReplicaStatus.RUNNING, "worker-a", NOW.plusSeconds(30), 1, null,
                null, null, NOW, NOW);

        assertThat(slot.matchesVersion(8L)).isTrue();
        assertThat(slot.matchesVersion(7L)).isFalse();
        assertThat(replica.ownsLease(4L, "worker-a", NOW)).isTrue();
        assertThat(replica.ownsLease(3L, "worker-a", NOW)).isFalse();
        assertThat(replica.ownsLease(4L, "worker-b", NOW)).isFalse();
        assertThat(replica.ownsLease(4L, "worker-a", NOW.plusSeconds(31))).isFalse();
    }

    private static AppSourceSnapshot snapshot(
            AppSourcePurpose purpose,
            UserId ownerUserId,
            List<AppSourceSelectedPath> selectedPaths) {
        return snapshotWithExpiry(purpose, ownerUserId, selectedPaths, NOW.plus(Duration.ofHours(48)));
    }

    private static AppSourceSnapshot snapshotWithExpiry(Instant expiresAt) {
        return snapshotWithExpiry(
                AppSourcePurpose.PERSONAL,
                new UserId("usr_owner"),
                List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                expiresAt);
    }

    private static AppSourceSnapshot snapshotWithExpiry(
            AppSourcePurpose purpose,
            UserId ownerUserId,
            List<AppSourceSelectedPath> selectedPaths,
            Instant expiresAt) {
        return new AppSourceSnapshot(
                new CodeRepositoryId("repo_source"), 1L, "source-repo", purpose, ownerUserId,
                "main", "0123456789abcdef", selectedPaths, null, NOW, expiresAt,
                AppSourceSnapshotStatus.PENDING, NOW, NOW);
    }
}
